package br.com.nutriplan.prescricao.service;

import br.com.nutriplan.prescricao.dto.PrescricaoDtos;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * O plano alimentar em papel.
 *
 * Existe porque o paciente nao tem conta no sistema: ele le o plano pelo link
 * ou o leva impresso, e o impresso e o unico que funciona na cozinha sem
 * bateria. A folha e entregue na consulta, entao ela precisa se bastar — traz
 * o consultorio, o profissional, o CRN e a vigencia, e nao depende de nada que
 * so exista na tela.
 *
 * O desenho repete a regua do dia da interface: a hora a esquerda, a refeicao
 * pendurada nela, e a medida caseira em corpo maior que o nome do alimento.
 * Nao e capricho — e a mesma razao da tela. O paciente ja sabe o que e arroz;
 * o que ele nao sabe e quanto.
 */
@Component
public class GeradorDePdfDoPlano {

    private static final Color BREU = new Color(0x13, 0x1c, 0x18);
    private static final Color BETERRABA = new Color(0x5e, 0x1a, 0x46);
    private static final Color TINTA_MEDIA = new Color(0x56, 0x61, 0x59);
    private static final Color LINHA = new Color(0xc4, 0xca, 0xc1);

    private static final Font TITULO = fonte(20, Font.BOLD, BREU);
    private static final Font SUBTITULO = fonte(10, Font.NORMAL, TINTA_MEDIA);
    private static final Font ETIQUETA = fonte(7.5f, Font.BOLD, TINTA_MEDIA);
    private static final Font HORA = fonte(12, Font.BOLD, BREU);
    private static final Font REFEICAO = fonte(13, Font.BOLD, BREU);
    private static final Font ALIMENTO = fonte(9.5f, Font.NORMAL, BREU);
    private static final Font PORCAO = fonte(12, Font.BOLD, BETERRABA);
    private static final Font PESO = fonte(8, Font.NORMAL, TINTA_MEDIA);
    private static final Font NOTA = fonte(8.5f, Font.ITALIC, TINTA_MEDIA);
    private static final Font RODAPE = fonte(8, Font.NORMAL, TINTA_MEDIA);

    private static Font fonte(float tamanho, int estilo, Color cor) {
        // Helvetica e uma das 14 fontes base do PDF: nao precisa ser embutida,
        // e nenhuma instalacao de leitor deixa de te-la.
        return FontFactory.getFont(FontFactory.HELVETICA, "Cp1252", tamanho, estilo, cor);
    }

    /** A4 (595pt) menos as margens de 48pt declaradas na abertura do documento. */
    private static final float LARGURA_UTIL = 595 - 96;
    /** Meia pagina: a figura ilustra a orientacao, nao ocupa a folha sozinha. */
    private static final float ALTURA_MAXIMA_DA_FIGURA = 320;

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/uuuu");

    public byte[] gerar(PrescricaoDtos.PlanoPublicoResponse plano, boolean rascunho) {
        return gerar(plano, rascunho, Map.of());
    }

    /**
     * @param figuras conteudo das imagens das orientacoes, pela chave do anexo.
     *                Vem de fora porque carregar arquivo e trabalho de quem tem
     *                a transacao aberta, nao de quem desenha a folha.
     */
    public byte[] gerar(PrescricaoDtos.PlanoPublicoResponse plano, boolean rascunho,
                        Map<Long, byte[]> figuras) {
        var saida = new ByteArrayOutputStream();
        var documento = new Document(PageSize.A4, 48, 48, 44, 52);
        PdfWriter escritor = PdfWriter.getInstance(documento, saida);
        escritor.setPageEvent(new Rodape(plano));

        documento.open();
        try {
            if (rascunho) {
                // O profissional pode querer imprimir para conferir antes de
                // publicar. A folha precisa dizer o que e, senao chega ao
                // paciente como se fosse prescricao.
                documento.add(aviso("RASCUNHO — CONFERÊNCIA INTERNA, NÃO ENTREGUE AO PACIENTE"));
            }
            if (plano.encerrado()) {
                documento.add(aviso("PLANO ENCERRADO — NÃO É MAIS O PLANO VIGENTE"));
            }

            documento.add(cabecalho(plano));
            if (temTexto(plano.orientacoes())) {
                documento.add(orientacoes(plano.orientacoes()));
            }
            documento.add(refeicoes(plano));
            // Depois das refeicoes: quem le a folha na cozinha procura a
            // refeicao, nao o texto educativo.
            for (var anexo : plano.orientacoesAnexadas()) {
                documento.add(blocoDeTexto(anexo.titulo(), anexo.corpo()));
                Element figura = figura(figuras.get(anexo.id()));
                if (figura != null) {
                    documento.add(figura);
                }
            }
            if (plano.resumo() != null && plano.resumo().energiaKcal() != null) {
                documento.add(resumo(plano.resumo()));
            }
        } finally {
            documento.close();
        }
        return saida.toByteArray();
    }

    // ------------------------------------------------------------------- partes

    private Element aviso(String texto) {
        var celula = new PdfPCell(new Phrase(texto, fonte(9, Font.BOLD, Color.WHITE)));
        celula.setBackgroundColor(BETERRABA);
        celula.setPadding(7);
        celula.setBorder(0);
        var tabela = new PdfPTable(1);
        tabela.setWidthPercentage(100);
        tabela.setSpacingAfter(14);
        tabela.addCell(celula);
        return tabela;
    }

    private Element cabecalho(PrescricaoDtos.PlanoPublicoResponse plano) {
        var bloco = new Paragraph();
        if (temTexto(plano.consultorioNome())) {
            bloco.add(new Phrase(plano.consultorioNome().toUpperCase() + "\n", ETIQUETA));
        }
        bloco.add(new Phrase(plano.titulo() + "\n", TITULO));

        var linha = new StringBuilder();
        if (temTexto(plano.pacienteNome())) {
            linha.append(plano.pacienteNome());
        }
        if (temTexto(plano.nutricionistaNome())) {
            if (!linha.isEmpty()) {
                linha.append("  ·  ");
            }
            linha.append("Elaborado por ").append(plano.nutricionistaNome());
            if (temTexto(plano.nutricionistaCrn())) {
                linha.append(" (").append(plano.nutricionistaCrn()).append(")");
            }
        }
        if (!linha.isEmpty()) {
            bloco.add(new Phrase("\n" + linha + "\n", SUBTITULO));
        }
        if (plano.vigenciaInicio() != null) {
            String vigencia = "Vigência: " + plano.vigenciaInicio().format(DATA)
                    + (plano.vigenciaFim() != null
                            ? " até " + plano.vigenciaFim().format(DATA) : " em diante");
            bloco.add(new Phrase(vigencia + "\n", ETIQUETA));
        }
        bloco.setSpacingAfter(16);
        return bloco;
    }

    /**
     * A figura da orientacao, encaixada na largura util da folha.
     *
     * Uma imagem maior que a pagina faz o leitor cortar em vez de reduzir, e o
     * paciente recebe meio prato. Arquivo que o leitor de imagem nao reconhece
     * some da folha em silencio: o texto da orientacao ja esta impresso acima,
     * e uma folha sem a figura serve melhor que uma folha que nao sai.
     */
    private Element figura(byte[] conteudo) {
        if (conteudo == null || conteudo.length == 0) {
            return null;
        }
        try {
            var imagem = com.lowagie.text.Image.getInstance(conteudo);
            imagem.scaleToFit(LARGURA_UTIL, ALTURA_MAXIMA_DA_FIGURA);
            imagem.setAlignment(Element.ALIGN_LEFT);
            imagem.setSpacingAfter(16);
            return imagem;
        } catch (Exception e) {
            return null;
        }
    }

    private Element orientacoes(String texto) {
        return blocoDeTexto("ORIENTAÇÕES GERAIS", texto);
    }

    /** Texto com titulo, marcado pela barra de beterraba na margem. */
    private Element blocoDeTexto(String titulo, String corpo) {
        var tabela = new PdfPTable(1);
        tabela.setWidthPercentage(100);
        tabela.setSpacingAfter(16);
        tabela.setKeepTogether(true);

        var celula = new PdfPCell();
        celula.setBorder(PdfPCell.LEFT);
        celula.setBorderColor(BETERRABA);
        celula.setBorderWidthLeft(2.5f);
        celula.setPaddingLeft(10);
        celula.setPaddingTop(2);
        celula.setPaddingBottom(6);
        celula.addElement(new Paragraph(titulo.toUpperCase(), ETIQUETA));
        celula.addElement(new Paragraph(corpo, fonte(9.5f, Font.NORMAL, BREU)));
        tabela.addCell(celula);
        return tabela;
    }

    /**
     * As refeicoes, na regua do dia.
     *
     * Duas colunas: a hora e o conteudo. A borda esquerda da segunda coluna e a
     * linha vertical que amarra o dia — o mesmo elemento da tela, aqui feito de
     * borda de celula porque e o que o PDF sabe desenhar sem posicionamento
     * absoluto.
     */
    private Element refeicoes(PrescricaoDtos.PlanoPublicoResponse plano) {
        var tabela = new PdfPTable(new float[] {1f, 6.4f});
        tabela.setWidthPercentage(100);
        tabela.setSpacingAfter(14);
        // Cabecalho de refeicao nao pode terminar a pagina sozinho.
        tabela.setSplitLate(true);

        for (var refeicao : plano.refeicoes()) {
            tabela.addCell(celulaDaHora(refeicao));
            tabela.addCell(celulaDaRefeicao(refeicao));
        }
        return tabela;
    }

    private PdfPCell celulaDaHora(PrescricaoDtos.RefeicaoPublicaResponse refeicao) {
        String texto = refeicao.horario() != null
                ? refeicao.horario().toString().substring(0, 5)
                : "LIVRE";
        var celula = new PdfPCell(new Phrase(texto,
                refeicao.horario() != null ? HORA : ETIQUETA));
        celula.setBorder(0);
        celula.setHorizontalAlignment(Element.ALIGN_RIGHT);
        celula.setPaddingTop(10);
        celula.setPaddingRight(10);
        return celula;
    }

    private PdfPCell celulaDaRefeicao(PrescricaoDtos.RefeicaoPublicaResponse refeicao) {
        var celula = new PdfPCell();
        celula.setBorder(PdfPCell.LEFT);
        celula.setBorderColor(LINHA);
        celula.setBorderWidthLeft(1f);
        celula.setPaddingLeft(12);
        celula.setPaddingTop(6);
        celula.setPaddingBottom(14);

        celula.addElement(new Paragraph(refeicao.nome(), REFEICAO));
        if (temTexto(refeicao.observacao())) {
            celula.addElement(new Paragraph(refeicao.observacao(), NOTA));
        }

        for (var item : refeicao.itens()) {
            var bloco = new Paragraph();
            bloco.setSpacingBefore(7);
            bloco.add(new Phrase(item.descricao() + "\n", ALIMENTO));
            bloco.add(new Phrase(item.porcao(), PORCAO));
            if (item.pesoGramas() != null && !ehPesoEmGramas(item.porcao())) {
                bloco.add(new Phrase("   " + formatarPeso(item.pesoGramas()), PESO));
            }
            celula.addElement(bloco);

            if (temTexto(item.observacao())) {
                celula.addElement(new Paragraph(item.observacao(), NOTA));
            }
            for (var substituicao : item.substituicoes()) {
                var alternativa = new Paragraph(
                        "ou  " + substituicao.descricao() + " — " + substituicao.porcao(),
                        fonte(8.5f, Font.NORMAL, TINTA_MEDIA));
                alternativa.setIndentationLeft(12);
                celula.addElement(alternativa);
            }
        }
        return celula;
    }

    private Element resumo(PrescricaoDtos.ResumoPublicoResponse resumo) {
        var tabela = new PdfPTable(4);
        tabela.setWidthPercentage(100);
        tabela.addCell(celulaDoResumo("ENERGIA", resumo.energiaKcal(), "kcal"));
        tabela.addCell(celulaDoResumo("PROTEÍNAS", resumo.proteinaG(), "g"));
        tabela.addCell(celulaDoResumo("CARBOIDRATOS", resumo.carboidratoG(), "g"));
        tabela.addCell(celulaDoResumo("GORDURAS", resumo.lipideosG(), "g"));

        var envolucro = new PdfPTable(1);
        envolucro.setWidthPercentage(100);
        var celula = new PdfPCell();
        celula.setBorder(PdfPCell.TOP);
        celula.setBorderColor(LINHA);
        celula.setPaddingTop(10);
        celula.addElement(new Paragraph("RESUMO DO DIA", ETIQUETA));
        celula.addElement(tabela);
        celula.addElement(new Paragraph(
                "Valores estimados a partir das tabelas de composição de alimentos.",
                fonte(7.5f, Font.NORMAL, TINTA_MEDIA)));
        envolucro.addCell(celula);
        return envolucro;
    }

    private PdfPCell celulaDoResumo(String rotulo, BigDecimal valor, String unidade) {
        var celula = new PdfPCell();
        celula.setBorder(0);
        celula.setPaddingTop(6);
        celula.setPaddingBottom(8);
        celula.addElement(new Paragraph(rotulo, ETIQUETA));
        celula.addElement(new Paragraph(
                valor == null ? "—" : arredondar(valor) + " " + unidade,
                fonte(13, Font.BOLD, BREU)));
        return celula;
    }

    /** Rodape em toda pagina: o papel circula solto, e precisa se identificar. */
    private static class Rodape extends com.lowagie.text.pdf.PdfPageEventHelper {
        private final PrescricaoDtos.PlanoPublicoResponse plano;

        Rodape(PrescricaoDtos.PlanoPublicoResponse plano) {
            this.plano = plano;
        }

        @Override
        public void onEndPage(PdfWriter escritor, Document documento) {
            String esquerda = plano.titulo()
                    + (plano.pacienteNome() != null ? "  ·  " + plano.pacienteNome() : "");
            String direita = "Emitido em " + LocalDate.now().format(DATA)
                    + "  ·  página " + documento.getPageNumber();

            var tabela = new PdfPTable(2);
            tabela.setTotalWidth(documento.right() - documento.left());
            tabela.addCell(celula(esquerda, Element.ALIGN_LEFT));
            tabela.addCell(celula(direita, Element.ALIGN_RIGHT));
            tabela.writeSelectedRows(0, -1, documento.left(),
                    documento.bottom() - 8, escritor.getDirectContent());
        }

        private PdfPCell celula(String texto, int alinhamento) {
            var celula = new PdfPCell(new Phrase(texto, RODAPE));
            celula.setBorder(0);
            celula.setHorizontalAlignment(alinhamento);
            return celula;
        }
    }

    // ------------------------------------------------------------------- apoio

    private boolean temTexto(String texto) {
        return texto != null && !texto.isBlank();
    }

    /** A porcao ja e o proprio peso quando nao ha medida caseira: "100 g". */
    private boolean ehPesoEmGramas(String porcao) {
        return porcao != null && porcao.matches("\\s*[\\d.,]+\\s*[gG]\\s*");
    }

    private String formatarPeso(BigDecimal gramas) {
        return gramas.stripTrailingZeros().toPlainString().replace(".", ",") + " g";
    }

    /**
     * Arredonda preservando a ordem de grandeza — a mesma regra da tela.
     * Imprimir "0 g" onde ha 0,23 g de gordura afirmaria ausencia, que e
     * diferente de "pouca".
     */
    private String arredondar(BigDecimal valor) {
        int casas = valor.compareTo(BigDecimal.valueOf(100)) >= 0 ? 0
                : valor.compareTo(BigDecimal.TEN) >= 0 ? 1 : 2;
        return valor.setScale(casas, RoundingMode.HALF_UP).toPlainString().replace(".", ",");
    }
}
