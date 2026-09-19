// 하루제주 부하테스트 (k6)
//
//   k6 run -e SCENARIO=mixed|limit -e JWT_SECRET=... loadtest/jejuday.js
//
//   mixed : constant-arrival-rate 50 TPS, 걸음수 60% + 알림 40% 혼합 (리마인더 수동 실행은 run.py 가 담당)
//   limit : ramping-arrival-rate 로 TPS 를 단계적으로 올려 한계 TPS 탐색
//
// 인증: JwtRequestFilter 는 accessToken 쿠키의 JWT(HS512, subject=email)만 본다.
//       로그인(BCrypt) 비용이 섞이지 않도록 토큰을 k6 안에서 직접 서명한다.
// 전제: loadtest/seed.sql 로 lt{1..USERS}@loadtest.local 이 시드되어 있어야 한다.
import http from 'k6/http';
import crypto from 'k6/crypto';
import encoding from 'k6/encoding';
import exec from 'k6/execution';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://localhost:8081';
const SCENARIO = __ENV.SCENARIO || 'mixed';
const USERS = parseInt(__ENV.USERS || '10000');
const SECRET = __ENV.JWT_SECRET;
const DURATION = __ENV.DURATION || '25m';
const TPS = parseInt(__ENV.TPS || '50');
const STEPS_SHARE = 0.6; // 걸음수 : 알림 = 6 : 4
// 알림 발송 종류/비율. reply=키가 매번 고유(기본), step=STEP 알림(step-goal:{date} 로 유저·일 단위 동일 키 → 인박스 중복 재현용)
const SEND_KIND = __ENV.SEND_KIND || 'reply';
const SEND_SHARE = parseFloat(__ENV.SEND_SHARE || '0.10');

// 4xx 는 비즈니스 거절(한도/걸음수 부족)이라 에러가 아니다. 5xx·타임아웃(status 0)만 실패로 센다.
http.setResponseCallback(http.expectedStatuses({ min: 200, max: 499 }));

const convertFresh = new Counter('convert_fresh');
const convertReplay = new Counter('convert_replay');
const convertOk = new Counter('convert_ok');
const convertRejected = new Counter('convert_rejected');

const b64url = (s) => encoding.b64encode(s, 'rawurl');

function signJwt(email) {
  const now = Math.floor(Date.now() / 1000);
  const head = b64url(JSON.stringify({ alg: 'HS512', typ: 'JWT' }));
  const body = b64url(JSON.stringify({ sub: email, iat: now, exp: now + 6 * 3600 }));
  const sig = crypto.hmac('sha512', encoding.b64decode(SECRET), `${head}.${body}`, 'base64rawurl');
  return `${head}.${body}.${sig}`;
}

// VU 안에서 유저별 헤더를 캐시한다 (HMAC 은 유저당 한 번만)
const cache = {};
function headersFor(id) {
  if (!cache[id]) {
    cache[id] = { Cookie: `accessToken=${signJwt(`lt${id}@loadtest.local`)}`, 'Content-Type': 'application/json' };
  }
  return cache[id];
}
const randUser = () => 1 + Math.floor(Math.random() * USERS);
const uuid = () => `${Date.now().toString(36)}-${exec.vu.idInTest}-${exec.scenario.iterationInTest}-${Math.floor(Math.random() * 1e9)}`;

const thresholds = { http_req_failed: ['rate<0.01'], http_req_duration: ['p(95)<500'] };
const timeout = '10s';

// mixed: 50 TPS 고정. iteration 1회 = API 호출 1회가 되도록 흐름을 단순하게 유지한다.
const mixed = {
  steps: { executor: 'constant-arrival-rate', rate: Math.round(TPS * STEPS_SHARE), timeUnit: '1s', duration: DURATION, preAllocatedVUs: 60, maxVUs: 300, exec: 'stepsFlow' },
  notification: { executor: 'constant-arrival-rate', rate: Math.round(TPS * (1 - STEPS_SHARE)), timeUnit: '1s', duration: DURATION, preAllocatedVUs: 40, maxVUs: 300, exec: 'notificationFlow' },
};

// limit: START(기본 STEP) TPS 부터 STEP 씩 올려 HOLD 동안 유지. 단계별 에러율/p95 는 run.py 가 JSON 출력에서 계산한다.
const STEP = parseInt(__ENV.STEP || '50');
const START = parseInt(__ENV.START || String(STEP)); // 첫 단계 TPS (기본: STEP)
const MAX_TPS = parseInt(__ENV.MAX_TPS || '600');
const HOLD = __ENV.HOLD || '30s';
const stages = [];
for (let r = START; r <= MAX_TPS; r += STEP) {
  stages.push({ target: r, duration: '5s' }, { target: r, duration: HOLD });
}
const limit = {
  steps: { executor: 'ramping-arrival-rate', startRate: Math.round(START * STEPS_SHARE), timeUnit: '1s', preAllocatedVUs: 200, maxVUs: 1500, stages: stages.map((s) => ({ ...s, target: Math.round(s.target * STEPS_SHARE) })), exec: 'stepsFlow' },
  notification: { executor: 'ramping-arrival-rate', startRate: Math.round(START * (1 - STEPS_SHARE)), timeUnit: '1s', preAllocatedVUs: 100, maxVUs: 1500, stages: stages.map((s) => ({ ...s, target: Math.round(s.target * (1 - STEPS_SHARE)) })), exec: 'notificationFlow' },
};

export const options = {
  scenarios: SCENARIO === 'limit' ? limit : mixed,
  thresholds: SCENARIO === 'limit' ? {} : thresholds,
  setupTimeout: '600s',
  discardResponseBodies: false,
};

// 전환(convert)이 성공할 수 있도록 모든 유저에게 걸음수를 미리 올려 둔다. (측정 구간 밖, name=setup)
export function setup() {
  const per = 50;
  for (let from = 1; from <= USERS; from += per) {
    const reqs = [];
    for (let id = from; id < from + per && id <= USERS; id++) {
      reqs.push(['POST', `${BASE}/v1/steps`, JSON.stringify({ stepCount: 3000 }), { headers: headersFor(id), tags: { name: 'setup' }, timeout }]);
    }
    http.batch(reqs);
  }
  console.log('SETUP_DONE'); // run.py 가 이 줄로 측정 시작 시각(t0)을 잡는다
  return { startedAt: Date.now() };
}

// 걸음수 트래픽: 업로드 70% / 포인트 전환 30%.
// 전환 요청의 일부는 직전 요청을 같은 requestId 로 재전송한다(재시도) — 전체 걸음수 요청의 10%.
let lastConvert = null; // VU 로컬: 직전 전환 요청
export function stepsFlow() {
  const p = Math.random();
  if (p < 0.10 && lastConvert) {
    // 같은 멱등 키 재전송
    convertReplay.add(1);
    const r = http.post(`${BASE}/v1/steps/convert`, lastConvert.body, { headers: headersFor(lastConvert.user), tags: { name: 'POST /v1/steps/convert (replay)' }, timeout });
    check(r, { 'replay not 5xx': (x) => x.status > 0 && x.status < 500 });
  } else if (p < 0.40) {
    const user = randUser();
    const body = JSON.stringify({ requestedPoints: 10 * (1 + Math.floor(Math.random() * 5)), requestId: uuid() });
    lastConvert = { user, body };
    convertFresh.add(1);
    const r = http.post(`${BASE}/v1/steps/convert`, body, { headers: headersFor(user), tags: { name: 'POST /v1/steps/convert' }, timeout });
    if (r.status === 200) convertOk.add(1); else if (r.status < 500) convertRejected.add(1);
    check(r, { 'convert not 5xx': (x) => x.status > 0 && x.status < 500 });
  } else {
    const user = randUser();
    const r = http.post(`${BASE}/v1/steps`, JSON.stringify({ stepCount: 100 + Math.floor(Math.random() * 300) }), { headers: headersFor(user), tags: { name: 'POST /v1/steps' }, timeout });
    check(r, { 'steps 200': (x) => x.status === 200 });
  }
}

// 알림 트래픽: 발송 10% / 목록 60% / 안읽은 수 30%.
// 발송은 dedup 키가 매번 달라지는 /reply(post:{postId}:reply)를 써서 실제로 outbox 에 쌓이게 한다.
// (STEP 알림은 step-goal:{date} 로 유저당 하루 1건만 들어가 outbox 부하가 되지 않는다)
// 발송 비율을 낮춘 이유: OutboxPoller 처리량이 최대 100건/10초라 발송이 많으면 리마인더 7,000건 배수 시간이 왜곡된다.
export function notificationFlow() {
  const user = randUser();
  const h = headersFor(user);
  const p = Math.random();
  if (p < SEND_SHARE && SEND_KIND === 'step') {
    const r = http.post(`${BASE}/v1/test-notification/step?userId=${user}&steps=20000`, null, { headers: h, tags: { name: 'POST /v1/test-notification/step (send)' }, timeout });
    check(r, { 'send 200': (x) => x.status === 200 });
  } else if (p < SEND_SHARE) {
    const postId = exec.scenario.iterationInTest * 1000 + exec.vu.idInTest; // 요청마다 유일
    const r = http.post(`${BASE}/v1/test-notification/reply?userId=${user}&postId=${postId}`, null, { headers: h, tags: { name: 'POST /v1/test-notification/reply (send)' }, timeout });
    check(r, { 'send 200': (x) => x.status === 200 });
  } else if (p < SEND_SHARE + (1 - SEND_SHARE) * 0.66) {
    const r = http.get(`${BASE}/v1/notification`, { headers: h, tags: { name: 'GET /v1/notification' }, timeout });
    check(r, { 'list 200': (x) => x.status === 200 });
  } else {
    const r = http.get(`${BASE}/v1/notification/unread-count`, { headers: h, tags: { name: 'GET /v1/notification/unread-count' }, timeout });
    check(r, { 'unread 200': (x) => x.status === 200 });
  }
}
