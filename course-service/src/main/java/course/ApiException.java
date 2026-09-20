package course;

// The service reports a stable error code
// The ApiAdvice chooses the translated message
class ApiException extends RuntimeException {
    final int status;
    final String code;

    ApiException(int status, String code) {
        super(code);
        this.status = status;
        this.code = code;
    }

}
