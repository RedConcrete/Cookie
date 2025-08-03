package cookie.server.service;

import cookie.server.dto.UserDto;
import cookie.server.dto.UserInformationDto;
import cookie.server.entitiy.UserEntity;
import cookie.server.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserInformationDto createUser(String userId, UserDto dto) {
        if (userRepository.existsById(userId)) {
            throw new IllegalArgumentException("User already exists");
        }

        UserEntity entity = new UserEntity();
        entity.setSteamId(userId);
        entity.setToken(dto.getToken());
        entity.setCookies(0);
        entity.setSugar(0);
        entity.setFlour(0);
        entity.setEggs(0);
        entity.setButter(0);
        entity.setChocolate(0);
        entity.setMilk(0);

        userRepository.save(entity);
        return toDto(entity);
    }

    public UserInformationDto getUser(String userId) {
        return userRepository.findById(userId)
                .map(this::toDto)
                .orElseThrow(() -> new NoSuchElementException("User not found"));
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

