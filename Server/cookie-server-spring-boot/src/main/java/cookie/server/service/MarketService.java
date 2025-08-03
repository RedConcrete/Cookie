package cookie.server.service;

import cookie.server.dto.MarketDto;
import cookie.server.dto.MarketRequestDto;
import cookie.server.dto.UserInformationDto;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
public class MarketService {
    public List<MarketDto> getMarketData(int amount) {
        List<MarketDto> markets = new ArrayList<>();
        Random random = new Random();

        for (int i = 0; i < amount; i++) {
            MarketDto dto = new MarketDto();
            dto.setDate(LocalDateTime.now().minusMinutes(i * 5));
            dto.setSugarPrice(random.nextDouble(0.5, 2.0));
            dto.setFlourPrice(random.nextDouble(0.5, 2.0));
            dto.setEggsPrice(random.nextDouble(0.5, 2.0));
            dto.setButterPrice(random.nextDouble(0.5, 2.0));
            dto.setChocolatePrice(random.nextDouble(0.5, 2.0));
            dto.setMilkPrice(random.nextDouble(0.5, 2.0));

            markets.add(dto);
        }

        return markets;
    }

    public UserInformationDto performAction(MarketRequestDto request) {
        return new UserInformationDto();
    }
}
