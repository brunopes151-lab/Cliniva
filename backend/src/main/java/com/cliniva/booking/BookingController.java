package com.cliniva.booking;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cliniva.agenda.dtos.DisponibilidadeDiaDTO;
import com.cliniva.booking.dtos.BookingRequestDTO;
import com.cliniva.booking.dtos.BookingResponseDTO;
import com.cliniva.booking.dtos.ServicoPublicoDTO;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/public/booking")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final BookingRateLimiter rateLimiter;

    @GetMapping("/{slug}/servicos")
    public List<ServicoPublicoDTO> listarServicos(@PathVariable String slug) {
        return bookingService.listarServicos(slug);
    }

    @GetMapping("/{slug}/disponibilidade")
    public DisponibilidadeDiaDTO disponibilidadeDia(
            @PathVariable String slug,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            @RequestParam UUID servicoId) {
        return bookingService.disponibilidade(slug, data, servicoId);
    }

    @PostMapping("/{slug}")
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponseDTO agendar(@PathVariable String slug,
            @Valid @RequestBody BookingRequestDTO request,
            HttpServletRequest httpRequest) {
        rateLimiter.registrar(chaveDaOrigem(slug, request.telefone(), httpRequest));
        return bookingService.agendar(slug, request);
    }

    private String chaveDaOrigem(String slug, String telefone, HttpServletRequest request) {
        StringBuilder forwarded = new StringBuilder();
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            forwarded.append(xForwardedFor.split(",")[0].trim());
        } else if (request.getRemoteAddr() != null) {
            forwarded.append(request.getRemoteAddr());
        }
        return slug + "|" + telefoneNormalizado(telefone) + "|" + forwarded;
    }

    private String telefoneNormalizado(String telefone) {
        if (telefone == null) {
            return "";
        }
        String digitos = telefone.replaceAll("\\D", "");
        return digitos.startsWith("55") ? digitos : "55" + digitos;
    }
}
