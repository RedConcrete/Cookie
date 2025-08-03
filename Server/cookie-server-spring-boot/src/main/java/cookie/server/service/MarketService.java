package cookie.server.service;

import cookie.server.dto.MarketDto;
import cookie.server.dto.MarketRequestDto;
import cookie.server.dto.UserInformationDto;

import java.util.List;

public class MarketService {

    public UserInformationDto performAction(MarketRequestDto request) {
        return new UserInformationDto();
    }

    public Object getMarketData(int amount) {
    }
}
