package br.com.nutriplan.paciente.service;

import br.com.nutriplan.paciente.domain.Sexo;
import br.com.nutriplan.paciente.dto.PacienteRequest;
import br.com.nutriplan.shared.util.LeitorCsv;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Le uma planilha de pacientes em CSV e devolve os cadastros prontos.
 *
 * Existe porque a migracao de outro sistema — ou da planilha que o consultorio
 * mantinha antes — e o primeiro obstaculo real de quem adota o sistema. Digitar
 * duzentos pacientes a mao nao e uma opcao, e sem os pacientes nada mais do
 * produto pode ser experimentado.
 *
 * Segue o mesmo desenho do importador de alimentos: casamento de coluna
 * tolerante a acento, caixa e separador, e aviso acumulado em vez de falha na
 * primeira linha torta. Uma planilha real sempre tem uma linha torta, e abortar
 * tudo por causa dela obrigaria o usuario a caçar o defeito sem pista.
 */
@Component
@Slf4j
public class ImportadorDePacientes {

    /** Nomes alternativos aceitos para cada coluna. */
    private static final Map<String, List<String>> ALIAS = Map.of(
            "nome",           List.of("nome", "nomecompleto", "paciente", "nomedopaciente"),
            "email",          List.of("email", "e-mail", "correioeletronico"),
            "telefone",       List.of("telefone", "celular", "fone", "whatsapp", "contato"),
            "datanascimento", List.of("datanascimento", "nascimento", "datadenascimento",
                                      "aniversario", "dtnascimento"),
            "sexo",           List.of("sexo", "genero"),
            "cpf",            List.of("cpf", "documento"),
            "profissao",      List.of("profissao", "ocupacao"),
            "objetivo",       List.of("objetivo", "meta", "queixa"),
            "observacoes",    List.of("observacoes", "observacao", "obs", "anotacoes")
    );

    /** Formatos de data que aparecem em planilha brasileira. */
    private static final List<DateTimeFormatter> FORMATOS_DE_DATA = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/uuuu"),
            DateTimeFormatter.ofPattern("d/M/uuuu"),
            DateTimeFormatter.ofPattern("dd-MM-uuuu"));

    /** Uma linha lida, com o numero de origem para o aviso poder apontá-la. */
    public record Linha(int numero, PacienteRequest paciente) {}

    public record Resultado(List<Linha> linhas, List<String> avisos, int ignoradas) {}

    public Resultado ler(Reader entrada, char separador) throws IOException {
        List<Linha> linhas = new ArrayList<>();
        List<String> avisos = new ArrayList<>();
        int ignoradas = 0;

        try (var reader = new BufferedReader(entrada)) {
            String cabecalho = reader.readLine();
            if (cabecalho == null) {
                avisos.add("O arquivo esta vazio.");
                return new Resultado(linhas, avisos, 0);
            }

            Map<String, Integer> colunas = mapearColunas(LeitorCsv.dividir(cabecalho, separador));
            if (!colunas.containsKey("nome")) {
                avisos.add("Não encontrei a coluna com o nome do paciente. "
                        + "Nomeie a coluna como \"nome\" ou \"paciente\".");
                return new Resultado(linhas, avisos, 0);
            }

            String linha;
            int numero = 1;
            while ((linha = reader.readLine()) != null) {
                numero++;
                if (linha.isBlank()) {
                    continue;
                }
                String[] campos = LeitorCsv.dividir(linha, separador);
                String nome = valor(campos, colunas.get("nome"));
                if (nome == null) {
                    ignoradas++;
                    if (avisos.size() < 20) {
                        avisos.add("Linha %d ignorada: sem nome.".formatted(numero));
                    }
                    continue;
                }

                LocalDate nascimento = data(valor(campos, colunas.get("datanascimento")));
                // Data ilegivel nao descarta o paciente: o nome e o contato ainda
                // valem, e a data pode ser corrigida na ficha depois.
                if (nascimento == null && valor(campos, colunas.get("datanascimento")) != null
                        && avisos.size() < 20) {
                    avisos.add("Linha %d: data de nascimento não reconhecida; paciente importado sem ela."
                            .formatted(numero));
                }
                if (nascimento != null && nascimento.isAfter(LocalDate.now())) {
                    nascimento = null;
                    if (avisos.size() < 20) {
                        avisos.add("Linha %d: data de nascimento no futuro, ignorada.".formatted(numero));
                    }
                }

                linhas.add(new Linha(numero, new PacienteRequest(
                        recortar(nome, 150),
                        recortar(email(valor(campos, colunas.get("email"))), 180),
                        recortar(valor(campos, colunas.get("telefone")), 20),
                        nascimento,
                        sexo(valor(campos, colunas.get("sexo"))),
                        recortar(somenteDigitos(valor(campos, colunas.get("cpf"))), 20),
                        recortar(valor(campos, colunas.get("profissao")), 100),
                        recortar(valor(campos, colunas.get("objetivo")), 500),
                        recortar(valor(campos, colunas.get("observacoes")), 2000))));
            }
        }

        if (ignoradas > 20) {
            avisos.add("... e mais %d linhas ignoradas pelo mesmo motivo.".formatted(ignoradas - 20));
        }
        return new Resultado(linhas, avisos, ignoradas);
    }

    private Map<String, Integer> mapearColunas(String[] cabecalho) {
        Map<String, Integer> encontradas = new HashMap<>();
        for (int i = 0; i < cabecalho.length; i++) {
            String normalizada = normalizar(cabecalho[i]);
            for (var alias : ALIAS.entrySet()) {
                if (alias.getValue().contains(normalizada)) {
                    encontradas.putIfAbsent(alias.getKey(), i);
                }
            }
        }
        return encontradas;
    }

    /** Remove acentos e tudo que nao for letra ou digito; devolve minusculo. */
    private String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return semAcento.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private String valor(String[] campos, Integer indice) {
        if (indice == null || indice < 0 || indice >= campos.length) {
            return null;
        }
        String bruto = campos[indice].trim();
        return bruto.isEmpty() ? null : bruto;
    }

    /** E-mail invalido entra como ausente: a coluna as vezes traz "-" ou "sem". */
    private String email(String texto) {
        if (texto == null) {
            return null;
        }
        return texto.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+") ? texto : null;
    }

    private String somenteDigitos(String texto) {
        if (texto == null) {
            return null;
        }
        String digitos = texto.replaceAll("[^0-9]", "");
        return digitos.isEmpty() ? null : digitos;
    }

    private Sexo sexo(String texto) {
        if (texto == null) {
            return null;
        }
        String n = normalizar(texto);
        if (n.startsWith("f")) {
            return Sexo.FEMININO;
        }
        if (n.startsWith("m")) {
            return Sexo.MASCULINO;
        }
        return null;
    }

    private LocalDate data(String texto) {
        if (texto == null) {
            return null;
        }
        for (DateTimeFormatter formato : FORMATOS_DE_DATA) {
            try {
                return LocalDate.parse(texto, formato);
            } catch (DateTimeParseException ignorado) {
                // tenta o proximo formato
            }
        }
        return null;
    }

    private String recortar(String texto, int limite) {
        if (texto == null) {
            return null;
        }
        return texto.length() <= limite ? texto : texto.substring(0, limite);
    }
}
