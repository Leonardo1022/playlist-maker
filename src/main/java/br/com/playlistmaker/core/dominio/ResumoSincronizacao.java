package br.com.playlistmaker.core.dominio;

public record ResumoSincronizacao(
        int totalCurtidas,
        int novasFaixas,
        int removidasFaixas,
        String mensagem
) {}
