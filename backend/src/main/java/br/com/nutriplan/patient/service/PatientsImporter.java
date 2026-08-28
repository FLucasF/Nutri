package br.com.nutriplan.patient.service;

import br.com.nutriplan.patient.domain.Sex;
import br.com.nutriplan.patient.dto.PatientRequest;
import br.com.nutriplan.shared.util.CsvReader;
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
 * Reads a spreadsheet of patients in CSV and returns the registrations ready.
 *
 * It exists because migrating from another system — or from the spreadsheet the
 * practice kept before — is the first real obstacle for whoever adopts the
 * system. Typing two hundred patients by hand is not an option, and without the
 * patients nothing else in the product can be tried out.
 *
 * It follows the same design as the food importer: column matching tolerant of
 * accents, case and separator, and a warning accumulated instead of failure at
 * the first crooked row. A real spreadsheet always has a crooked row, and
 * aborting everything because of it would force the user to hunt for the defect
 * with no clue.
 */
@Component
@Slf4j
public class PatientsImporter {

    /** Alternative names accepted for each column. */
    private static final Map<String, List<String>> ALIAS = Map.of(
            "name",           List.of("nome", "name", "nomecompleto", "paciente",
                                      "patient", "nomedopaciente"),
            "email",          List.of("email", "e-mail", "correioeletronico"),
            "phone",          List.of("telefone", "phone", "celular", "fone",
                                      "whatsapp", "contato"),
            "datanascimento", List.of("datanascimento", "nascimento", "birth",
                                      "datadenascimento", "aniversario",
                                      "dtnascimento"),
            "sex",            List.of("sexo", "sex", "genero"),
            "cpf",            List.of("cpf", "documento", "document"),
            "occupation",     List.of("profissao", "occupation", "ocupacao")
    );

    /** Date formats that show up in a Brazilian spreadsheet. */
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/uuuu"),
            DateTimeFormatter.ofPattern("d/M/uuuu"),
            DateTimeFormatter.ofPattern("dd-MM-uuuu"));

    /** One row read, with its source number so the warning can point at it. */
    public record Row(int number, PatientRequest patient) {}

    public record Result(List<Row> rows, List<String> warnings, int ignored) {}

    public Result read(Reader input, char separator) throws IOException {
        List<Row> rows = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int ignored = 0;

        try (var reader = new BufferedReader(input)) {
            String header = reader.readLine();
            if (header == null) {
                warnings.add("O arquivo esta vazio.");
                return new Result(rows, warnings, 0);
            }

            Map<String, Integer> columns = mapColumns(CsvReader.divide(header, separator));
            if (!columns.containsKey("name")) {
                warnings.add("Não encontrei a coluna com o nome do paciente. "
                        + "Nomeie a coluna como \"nome\" ou \"paciente\".");
                return new Result(rows, warnings, 0);
            }

            String row;
            int number = 1;
            while ((row = reader.readLine()) != null) {
                number++;
                if (row.isBlank()) {
                    continue;
                }
                String[] fields = CsvReader.divide(row, separator);
                String name = value(fields, columns.get("name"));
                if (name == null) {
                    ignored++;
                    if (warnings.size() < 20) {
                        warnings.add("Linha %d ignorada: sem nome.".formatted(number));
                    }
                    continue;
                }

                LocalDate birth = date(value(fields, columns.get("datanascimento")));
                // An unreadable date does not discard the patient: the name and the
                // contact still hold, and the date can be corrected on the
                // record later.
                if (birth == null && value(fields, columns.get("datanascimento")) != null
                        && warnings.size() < 20) {
                    warnings.add("Linha %d: data de nascimento não reconhecida; paciente importado sem ela."
                            .formatted(number));
                }
                if (birth != null && birth.isAfter(LocalDate.now())) {
                    birth = null;
                    if (warnings.size() < 20) {
                        warnings.add("Linha %d: data de nascimento no futuro, ignorada.".formatted(number));
                    }
                }

                rows.add(new Row(number, new PatientRequest(
                        trim(name, 150),
                        trim(email(value(fields, columns.get("email"))), 180),
                        trim(value(fields, columns.get("phone")), 20),
                        birth,
                        sex(value(fields, columns.get("sex"))),
                        trim(onlyDigits(value(fields, columns.get("cpf"))), 20),
                        trim(value(fields, columns.get("occupation")), 100),
                        trim(value(fields, columns.get("goal")), 500),
                        trim(value(fields, columns.get("notes")), 2000))));
            }
        }

        if (ignored > 20) {
            warnings.add("... e mais %d linhas ignoradas pelo mesmo motivo.".formatted(ignored - 20));
        }
        return new Result(rows, warnings, ignored);
    }

    private Map<String, Integer> mapColumns(String[] header) {
        Map<String, Integer> encontradas = new HashMap<>();
        for (int i = 0; i < header.length; i++) {
            String normalized = normalize(header[i]);
            for (var alias : ALIAS.entrySet()) {
                if (alias.getValue().contains(normalized)) {
                    encontradas.putIfAbsent(alias.getKey(), i);
                }
            }
        }
        return encontradas;
    }

    /** Removes accents and everything that is not a letter or digit; returns lowercase. */
    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        String withoutAccent = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return withoutAccent.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private String value(String[] fields, Integer index) {
        if (index == null || index < 0 || index >= fields.length) {
            return null;
        }
        String raw = fields[index].trim();
        return raw.isEmpty() ? null : raw;
    }

    /** An invalid email comes in as absent: the column sometimes carries "-" or "sem". */
    private String email(String text) {
        if (text == null) {
            return null;
        }
        return text.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+") ? text : null;
    }

    private String onlyDigits(String text) {
        if (text == null) {
            return null;
        }
        String digits = text.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? null : digits;
    }

    private Sex sex(String text) {
        if (text == null) {
            return null;
        }
        String n = normalize(text);
        if (n.startsWith("f")) {
            return Sex.FEMALE;
        }
        if (n.startsWith("m")) {
            return Sex.MALE;
        }
        return null;
    }

    private LocalDate date(String text) {
        if (text == null) {
            return null;
        }
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDate.parse(text, format);
            } catch (DateTimeParseException ignored) {
                // try the next format
            }
        }
        return null;
    }

    private String trim(String text, int limit) {
        if (text == null) {
            return null;
        }
        return text.length() <= limit ? text : text.substring(0, limit);
    }
}
