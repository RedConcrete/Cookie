package cookie.server.dto;

import java.util.List;

public class UserMarketDataDto {
    private UserInformationDto user;
    private List<MarketDto> markets;
    private List<RecipeDto> recipes;

    public UserMarketDataDto(UserInformationDto user, List<MarketDto> markets, List<RecipeDto> recipes) {
        this.user = user;
        this.markets = markets;
        this.recipes = recipes;
    }

    public UserInformationDto getUser() { return user; }
    public void setUser(UserInformationDto user) { this.user = user; }

    public List<MarketDto> getMarkets() { return markets; }
    public void setMarkets(List<MarketDto> markets) { this.markets = markets; }

    public List<RecipeDto> getRecipes() { return recipes; }
    public void setRecipes(List<RecipeDto> recipes) { this.recipes = recipes; }
}
