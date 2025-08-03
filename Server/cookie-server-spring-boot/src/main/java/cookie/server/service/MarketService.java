package cookie.server.service;

import cookie.server.dto.MarketDto;
import cookie.server.dto.MarketRequestDto;
import cookie.server.dto.UserInformationDto;
import cookie.server.entitiy.MarketEntity;
import cookie.server.repository.MarketRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.awt.print.Pageable;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MarketService {
    private final MarketRepository marketRepository;

    public MarketService(MarketRepository marketRepository) {
        this.marketRepository = marketRepository;
    }

    public List<MarketDto> getMarketData(int amount) {
        Pageable limit = (Pageable) PageRequest.of(0, amount);
        List<MarketEntity> markets = marketRepository.findAllByOrderByDateDesc(limit);

        return markets.stream()
                .map(MarketDto::new)
                .collect(Collectors.toList());
    }

    public List<MarketDto> getAllMarketData() {
        return marketRepository.findAll()
                .stream()
                .map(MarketDto::new)
                .collect(Collectors.toList());
    }

    public UserInformationDto performAction(MarketRequestDto request) {
        return new UserInformationDto();
    }
}
