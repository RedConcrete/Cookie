package cookie.server.dto;

public class UserDto {
    private String steamId;
    private String token;

    public String getToken() {
        return steamId;
    }

    public String getSteamId() {
        return steamId;
    }

    public void setSteamId(String steamId) {
        this.steamId = steamId;
    }

    public void setToken(String token) {
        this.token = token;
    }
}



