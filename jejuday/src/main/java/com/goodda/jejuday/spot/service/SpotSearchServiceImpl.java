package com.goodda.jejuday.spot.service;

import com.goodda.jejuday.spot.entity.Spot;
import com.goodda.jejuday.spot.entity.Spot.SpotType;
import com.goodda.jejuday.spot.repository.SpotRepository;
import com.goodda.jejuday.spot.search.SpotTrie;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Service
public class SpotSearchServiceImpl implements SpotSearchService {

    private final SpotRepository spotRepository;
    private final SpotTrie trie = new SpotTrie();

    // 지도에 노출할 타입 (POST 제외)
    private static final List<SpotType> MAP_TYPES =
        List.of(SpotType.SPOT, SpotType.CHALLENGE);

    @Autowired
    public SpotSearchServiceImpl(SpotRepository spotRepository) {
        this.spotRepository = spotRepository;
    }

    /**
     * 애플리케이션 시작 시 SPOT/CHALLENGE 이름으로 Trie 인덱스 초기화
     */
    @PostConstruct
    public void initTrie() {
        List<Spot> spots = spotRepository.findAllByTypeIn(MAP_TYPES);
        for (Spot s : spots) {
            trie.insert(s.getName(), s.getId());
        }
    }

    @Override
    public List<Spot> searchMapSpotsByTrie(String prefix) {
        Set<Long> ids = trie.searchByPrefix(prefix);
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        return spotRepository.findAllById(ids);
    }

    @Override
    public Page<Spot> searchCommunitySpotsBySql(String query, Pageable pageable) {
        // POST 포함: 필요 시 타입 필터 조정 가능
        List<SpotType> types = List.of(SpotType.POST, SpotType.SPOT, SpotType.CHALLENGE);
        return spotRepository.findByNameContainingIgnoreCaseAndTypeIn(query, types, pageable);
    }
}