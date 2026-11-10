package local.higanbana.web.error;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@ControllerAdvice(annotations = Controller.class)
public class GlobalExceptionHandler {
    @ExceptionHandler({IllegalArgumentException.class, BindException.class, MaxUploadSizeExceededException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String invalid(Exception exception, Model model) {
        String message = exception instanceof BindException ? "Revisa los campos obligatorios y sus longitudes."
                : exception instanceof MaxUploadSizeExceededException ? "El archivo supera el limite de 2 MB."
                : exception.getMessage();
        model.addAttribute("message", message);
        return "problem";
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String conflict(IllegalStateException exception, Model model) {
        model.addAttribute("message", "No se pudo completar la operacion. Comprueba el almacenamiento local y vuelve a intentar.");
        return "problem";
    }
}