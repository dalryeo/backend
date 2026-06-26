package com.ohgiraffers.dalryeo.user.exception;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class UserConstraintViolationTranslatorTest {

    @Test
    void translate_returnsDuplicatedNicknameWhenConstraintNameCaseDiffers() {
        ConstraintViolationException cause = new ConstraintViolationException(
                "duplicate nickname",
                new SQLException("duplicate key"),
                "USERS_NICKNAME_KEY"
        );
        DataIntegrityViolationException exception = new DataIntegrityViolationException("duplicate nickname", cause);

        RuntimeException translated = UserConstraintViolationTranslator.translate(exception);

        assertThat(translated)
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.DUPLICATED_NICKNAME);
    }
}
