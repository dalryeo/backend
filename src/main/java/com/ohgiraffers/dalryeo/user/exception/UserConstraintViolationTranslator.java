package com.ohgiraffers.dalryeo.user.exception;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

public final class UserConstraintViolationTranslator {

    private static final String NICKNAME_UNIQUE_CONSTRAINT = "users_nickname_key";

    private UserConstraintViolationTranslator() {
    }

    public static RuntimeException translate(DataIntegrityViolationException exception) {
        if (isNicknameUniqueConstraintViolation(exception)) {
            return new UserException(UserErrorCode.DUPLICATED_NICKNAME, exception);
        }
        return exception;
    }

    private static boolean isNicknameUniqueConstraintViolation(DataIntegrityViolationException exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof ConstraintViolationException constraintViolationException
                    && NICKNAME_UNIQUE_CONSTRAINT.equals(constraintViolationException.getConstraintName())) {
                return true;
            }
            if (containsNicknameConstraintName(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean containsNicknameConstraintName(Throwable throwable) {
        String message = throwable.getMessage();
        return message != null && message.contains(NICKNAME_UNIQUE_CONSTRAINT);
    }
}
