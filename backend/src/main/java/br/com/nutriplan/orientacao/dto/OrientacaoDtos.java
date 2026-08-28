package br.com.nutriplan.orientacao.dto;

import br.com.nutriplan.orientacao.domain.Orientacao;
import br.com.nutriplan.orientacao.domain.OrientacaoDoPlano;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class OrientacaoDtos {

    private OrientacaoDtos() {
    }

    public record OrientacaoRequest(
            @NotBlank @Size(max = 150) String titulo,
            @NotBlank @Size(max = 8000) String corpo
    ) {}

    public record OrientacaoResponse(
            Long id,
            String titulo,
            String corpo,
            /** Modelo do sistema nao e editavel: serve de ponto de partida. */
            boolean modeloDoSistema,
            boolean editavel,
            boolean temImagem,
            String imagemNome
    ) {
        public static OrientacaoResponse de(Orientacao o) {
            return new OrientacaoResponse(o.getId(), o.getTitulo(), o.getCorpo(),
                    o.ehModeloDoSistema(), !o.ehModeloDoSistema(),
                    o.temImagem(), o.getImagemNome());
        }
    }

    /** Pedido de anexo ao plano. O texto e copiado no momento do anexo. */
    public record AnexoRequest(
            /** Orientacao de origem. Nulo quando o texto e escrito na hora. */
            Long orientacaoId,
            @Size(max = 150) String titulo,
            @Size(max = 8000) String corpo
    ) {}

    public record OrientacaoDoPlanoResponse(
            Long id,
            Long orientacaoId,
            String titulo,
            String corpo,
            Integer ordem,
            boolean temImagem
    ) {
        public static OrientacaoDoPlanoResponse de(OrientacaoDoPlano o) {
            return new OrientacaoDoPlanoResponse(o.getId(), o.getOrientacaoId(),
                    o.getTitulo(), o.getCorpo(), o.getOrdem(), o.temImagem());
        }
    }

    public record ReordenarRequest(@NotNull java.util.List<Long> ids) {}
}
