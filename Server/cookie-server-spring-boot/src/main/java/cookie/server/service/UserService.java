package cookie.server.service;

import cookie.server.config.PlayerConfig;
import cookie.server.dto.UserDto;
import cookie.server.dto.UserInformationDto;
import cookie.server.entitiy.UserEntity;
import cookie.server.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PlayerConfig playerConfig;

    public UserService(UserRepository userRepository, PlayerConfig playerConfig) {
        this.userRepository = userRepository;
        this.playerConfig = playerConfig;
    }

    public UserInformationDto createUser(String userId, UserDto dto) {
        if (userRepository.existsById(userId)) {
            throw new IllegalArgumentException("User already exists");
        }

        UserEntity entity = new UserEntity();
        entity.setSteamId(userId);
        entity.setToken(dto.getToken());
        entity.setCookies(playerConfig.getInitialCookies());
        entity.setSugar(playerConfig.getInitialSugar());
        entity.setFlour(playerConfig.getInitialFlour());
        entity.setEggs(playerConfig.getInitialEggs());
        entity.setButter(playerConfig.getInitialButter());
        entity.setChocolate(playerConfig.getInitialChocolate());
        entity.setMilk(playerConfig.getInitialMilk());

        userRepository.save(entity);
        return toDto(entity);
    }

    public UserInformationDto getUser(String userId) {
        return userRepository.findById(userId)
                .map(this::toDto)
                .orElseGet(() -> {
                    UserEntity newUser = new UserEntity();
                    newUser.setSteamId(userId);
                    newUser.setCookies(playerConfig.getInitialCookies());
                    newUser.setSugar(playerConfig.getInitialSugar());
                    newUser.setFlour(playerConfig.getInitialFlour());
                    newUser.setEggs(playerConfig.getInitialEggs());
                    newUser.setButter(playerConfig.getInitialButter());
                    newUser.setChocolate(playerConfig.getInitialChocolate());
                    newUser.setMilk(playerConfig.getInitialMilk());
                    userRepository.save(newUser);
                    return toDto(newUser);
                });
    }


    public void deleteUser(String userId) {
        if (!userRepository.existsById(userId)) {
            throw new NoSuchElementException("User not found");
        }
        userRepository.deleteById(userId);
    }

    private UserInformationDto toDto(UserEntity entity) {
        UserInformationDto dto = new UserInformationDto();
        dto.setSteamId(entity.getSteamId());
        dto.setCookies(entity.getCookies());
        dto.setSugar(entity.getSugar());
        dto.setFlour(entity.getFlour());
        dto.setEggs(entity.getEggs());
        dto.setButter(entity.getButter());
        dto.setChocolate(entity.getChocolate());
        dto.setMilk(entity.getMilk());
        return dto;
    }
}
