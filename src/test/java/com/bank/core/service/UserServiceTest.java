package com.bank.core.service;

import com.bank.core.model.User;
import com.bank.core.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private static User user(long id) {
        User user = new User();
        user.setId(id);
        user.setName("Cliente " + id);
        user.setEmail("cliente" + id + "@test.com");
        user.setDocumentNumber("DOC-" + id);
        return user;
    }

    @Test
    @DisplayName("Delega la creación del usuario en el repositorio")
    void creaUsuario() {
        User toCreate = user(0L);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        User result = userService.createUser(toCreate);

        assertThat(result.getId()).isEqualTo(10L);
        verify(userRepository).save(toCreate);
    }

    @Test
    @DisplayName("Devuelve el usuario cuando existe")
    void obtieneUsuario() {
        User existing = user(5L);
        when(userRepository.findById(5L)).thenReturn(Optional.of(existing));

        assertThat(userService.getUser(5L)).isSameAs(existing);
    }

    @Test
    @DisplayName("Lanza NoSuchElementException cuando el usuario no existe")
    void usuarioInexistente() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUser(99L))
                .isInstanceOf(NoSuchElementException.class);
    }
}
