package com.yash.Finance.manager.repository;

import com.yash.Finance.manager.entity.Category;
import com.yash.Finance.manager.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByIsCustomFalse();
    List<Category> findByUserAndIsCustomTrue(User user);
    Optional<Category> findByNameAndUser(String name, User user);
    Optional<Category> findByNameAndIsCustomFalse(String name);
    boolean existsByNameAndUser(String name, User user);
}
