package com.cliniva.common;

import jakarta.servlet.http.HttpServletRequest;

/** IP de quem fez a requisição. */
public final class OrigemRequisicao {

    private OrigemRequisicao() {
    }

    /**
     * X-Forwarded-For usa o ÚLTIMO valor, que é o que o proxy da hospedagem
     * anexa; o primeiro é controlado pelo cliente.
     */
    public static String ip(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            String[] partes = xForwardedFor.split(",");
            return partes[partes.length - 1].trim();
        }
        return request.getRemoteAddr();
    }
}
