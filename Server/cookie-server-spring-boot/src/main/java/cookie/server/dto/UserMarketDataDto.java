package cookie.server.dto;

import java.util.List;

public class UserMarketDataDto {
    private UserInformationDto user;
    private List<MarketDto> markets;

    public UserMarketDataDto(UserInformationDto user, List<MarketDto> markets) {
        this.user = user;
        this.markets = markets;
    }

    public UserInformationDto getUser() {
        return user;
    }

    public void setUser(UserInformationDto user) {
        this.user = user;
    }

    public List<MarketDto> getMarkets() {
        return markets;
    }

    public void setMarkets(List<MarketDto> markets) {
        this.markets = markets;
    }
}
