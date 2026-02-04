package cookie.server.repository;

import cookie.server.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<UserEntity, String> {

    @Query("SELECT COALESCE(SUM(u.sugar), 0) FROM UserEntity u")
    double getTotalSugar();

    @Query("SELECT COALESCE(SUM(u.flour), 0) FROM UserEntity u")
    double getTotalFlour();

    @Query("SELECT COALESCE(SUM(u.eggs), 0) FROM UserEntity u")
    double getTotalEggs();

    @Query("SELECT COALESCE(SUM(u.butter), 0) FROM UserEntity u")
    double getTotalButter();

    @Query("SELECT COALESCE(SUM(u.chocolate), 0) FROM UserEntity u")
    double getTotalChocolate();

    @Query("SELECT COALESCE(SUM(u.milk), 0) FROM UserEntity u")
    double getTotalMilk();
}
