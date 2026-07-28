import os
import sys
import requests
from datetime import date
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

INGEST_URL = "https://jejuday.duckdns.org/api/crawler/ingest"
SELECTOR = "ul.event_list li.list_item"


def build_options():
    opts = webdriver.ChromeOptions()
    for arg in ["--headless=new", "--no-sandbox", "--disable-dev-shm-usage",
                "--disable-gpu", "--window-size=1920,1080"]:
        opts.add_argument(arg)
    return opts


def target_months():
    today = date.today()
    nxt = 1 if today.month == 12 else today.month + 1
    return [f"{today.month:02d}", f"{nxt:02d}"]


def collect():
    items = []
    driver = webdriver.Chrome(options=build_options())
    try:
        for month in target_months():
            url = f"https://www.visitjeju.net/kr/festival/list?month={month}&page=1"
            print(f"[GET] {url}")
            driver.get(url)
            try:
                WebDriverWait(driver, 20).until(
                    EC.presence_of_all_elements_located((By.CSS_SELECTOR, SELECTOR)))
            except Exception:
                print(f"  month={month}: 항목 없음, 건너뜀")
                continue
            found = [e.get_attribute("outerHTML")
                     for e in driver.find_elements(By.CSS_SELECTOR, SELECTOR)]
            print(f"  month={month}: {len(found)}건")
            items += found
    finally:
        driver.quit()
    return items


def main():
    token = os.environ.get("SYNC_TOKEN")
    if not token:
        print("SYNC_TOKEN 미설정")
        sys.exit(1)

    items = collect()
    if not items:
        print("수집 결과 없음 — 셀렉터 변경 가능성 확인 필요")
        sys.exit(1)

    print(f"총 {len(items)}건 전송 중...")
    res = requests.post(INGEST_URL, json=items,
                        headers={"X-Sync-Token": token}, timeout=120)
    res.raise_for_status()
    print("응답:", res.text)


if __name__ == "__main__":
    main()