package br.com.nutriplan.food.service;

import br.com.nutriplan.food.domain.Food;
import br.com.nutriplan.food.domain.DataSource;
import br.com.nutriplan.food.domain.HouseholdMeasure;
import br.com.nutriplan.food.dto.FoodDtos;
import br.com.nutriplan.food.dto.CompositionDto;
import br.com.nutriplan.food.repository.FoodRepository;
import br.com.nutriplan.food.repository.HouseholdMeasureRepository;
import br.com.nutriplan.auth.service.CurrentContext;
import br.com.nutriplan.shared.error.NotFoundException;
import br.com.nutriplan.shared.error.BusinessRuleException;
import br.com.nutriplan.shared.util.PluralMeasure;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FoodService {

    private final FoodRepository foodRepository;
    private final HouseholdMeasureRepository householdMeasureRepository;
    private final FoodsTableReader reader;
    private final CurrentContext contextCurrent;

    /**
     * Searches foods.
     *
     * With a term, it orders by relevance; without a term, it respects the
     * ordering the client asked for — usually alphabetical, which is what
     * makes sense when browsing a whole group.
     *
     * In the relevance search the Pageable deliberately goes without ordering:
     * Spring Data would append the client's sort to the query's ORDER BY, and
     * an "order by description" coming from the controller would cancel the
     * whole ranking.
     */
    @Transactional(readOnly = true)
    public Page<FoodDtos.Summary> find(String term, String group, DataSource source, Pageable pageable) {
        Long accountId = contextCurrent.accountId();
        String groupFilter = StringUtils.hasText(group) ? group : null;

        if (!StringUtils.hasText(term)) {
            return foodRepository
                    .find(accountId, null, groupFilter, source, pageable)
                    .map(FoodDtos.Summary::from);
        }

        var withoutOrdering = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return foodRepository
                .findByRelevance(accountId, Food.normalizeToSearch(term),
                        groupFilter, source, withoutOrdering)
                .map(FoodDtos.Summary::from);
    }

    @Transactional(readOnly = true)
    public FoodDtos.Detail detail(Long id) {
        Long accountId = contextCurrent.accountId();
        Food food = visibleRequire(id);
        return FoodDtos.Detail.from(
                food, householdMeasureRepository.visibleTo(id, accountId), accountId);
    }

    /**
     * Locates a processed product by barcode.
     *
     * The code is the natural way to reach the product when it is in the
     * patient's hand: the package has the EAN printed on it, and typing
     * thirteen digits is faster and less ambiguous than hunting for "Biscoito
     * recheado sabor chocolate 140g" among 21 thousand processed products with
     * irregular names.
     *
     * It returns a list because the code is not a key: there is duplication
     * inside Open Food Facts, which is collaborative, and the practice may have
     * registered its own product with the same EAN.
     */
    @Transactional(readOnly = true)
    public List<FoodDtos.Detail> byBarcodeCode(String code) {
        Long accountId = contextCurrent.accountId();
        String clean = code == null ? "" : code.replaceAll("[^0-9]", "");
        if (clean.isEmpty()) {
            throw new BusinessRuleException("Informe o código de barras, só com dígitos");
        }
        return foodRepository.byBarcodeCode(clean, accountId).stream()
                .map(a -> FoodDtos.Detail.from(
                        a, householdMeasureRepository.visibleTo(a.getId(), accountId), accountId))
                .toList();
    }

    /**
     * Registers a usual portion for the practice.
     *
     * It also works on foods from the public base — and that is precisely the
     * main case: TACO brings no portions, so the nutritionist needs to be able
     * to say that "1 tablespoon of rice" weighs 25 g in their practice. The
     * measure created is visible only to the practice that registered it.
     */
    @Transactional
    public FoodDtos.MeasureResponse addMeasure(Long foodId, FoodDtos.MeasureRequest req) {
        Long accountId = contextCurrent.accountId();
        Food food = visibleRequire(foodId);

        if (householdMeasureRepository.alreadyExistsAtAccount(foodId, accountId, req.description())) {
            throw new BusinessRuleException(
                    "Este consultório já tem uma medida chamada \"%s\" para este alimento"
                            .formatted(req.description()));
        }

        var measure = new HouseholdMeasure(req.description(), req.grams());
        measure.setFood(food);
        measure.setAccountId(accountId);
        measure.setStandard(req.standard());
        householdMeasureRepository.save(measure);

        if (req.standard()) {
            unmarkOthersStandard(foodId, accountId, measure.getId());
        }

        log.info("Medida caseira cadastrada: alimento={} conta={} descrição={}",
                foodId, accountId, req.description());
        return FoodDtos.MeasureResponse.from(measure, accountId);
    }

    @Transactional
    public void removeMeasure(Long foodId, Long measureId) {
        Long accountId = contextCurrent.accountId();
        HouseholdMeasure measure = householdMeasureRepository.visibleTo(measureId, foodId, accountId)
                .orElseThrow(() -> new NotFoundException("Medida caseira", measureId));

        if (!measure.editableBy(accountId)) {
            throw new BusinessRuleException(
                    "Porções que acompanham o sistema não podem ser removidas. "
                    + "Cadastre a sua própria versão, que terá precedência sobre ela.");
        }
        householdMeasureRepository.delete(measure);
    }

    /**
     * Guarantees a single standard measure per food within the practice.
     * Portions of the base catalog are not touched: they belong to every
     * practice.
     */
    private void unmarkOthersStandard(Long foodId, Long accountId, Long keepId) {
        householdMeasureRepository.visibleTo(foodId, accountId).stream()
                .filter(m -> !m.getId().equals(keepId))
                .filter(m -> m.editableBy(accountId))
                .filter(HouseholdMeasure::isStandard)
                .forEach(m -> m.setStandard(false));
    }

    @Transactional(readOnly = true)
    public List<String> listGroups() {
        return foodRepository.listGroups();
    }

    @Transactional(readOnly = true)
    public Food visibleRequire(Long id) {
        return foodRepository.visibleFind(id, contextCurrent.accountId())
                .orElseThrow(() -> new NotFoundException("Alimento", id));
    }

    /**
     * Calculates the composition of a portion.
     *
     * The quantity can come in grams or as a multiple of a household measure
     * ("2.5 tablespoons"); in the second case the weight is derived from the
     * measure.
     */
    @Transactional(readOnly = true)
    public FoodDtos.CalculatedServing calculateServing(Long foodId, BigDecimal quantity, Long measureId) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new BusinessRuleException("A quantidade deve ser maior que zero");
        }
        Food food = visibleRequire(foodId);

        BigDecimal grams;
        String measureUsed;
        if (measureId != null) {
            // Search filtered by account: a measure registered by another
            // practice cannot be used to calculate here.
            HouseholdMeasure measure = householdMeasureRepository
                    .visibleTo(measureId, foodId, contextCurrent.accountId())
                    .orElseThrow(() -> new NotFoundException(
                            "Medida caseira do alimento " + foodId, measureId));
            grams = measure.gramsTo(quantity);
            // The same agreement used in the prescription: "2 porções", not "2 porção".
            measureUsed = "%s %s".formatted(quantity.stripTrailingZeros().toPlainString(),
                    PluralMeasure.agree(quantity, measure.getDescription()));
        } else {
            grams = quantity;
            measureUsed = "%s g".formatted(quantity.stripTrailingZeros().toPlainString());
        }

        return new FoodDtos.CalculatedServing(
                food.getId(), food.getDescription(), grams, measureUsed,
                CompositionDto.from(food.compositionTo(grams)));
    }

    /**
     * Imports a food table sent by the nutritionist.
     *
     * The foods always come in tied to the account of whoever imported them,
     * never into the public base: a spreadsheet sent by one practice must not
     * change the catalog the others see, even if it declares TACO or TBCA as
     * its source.
     *
     * The reported source serves as provenance — the nutritionist is
     * technically answerable for the data they prescribe, and needs to know
     * where it came from.
     */
    @Transactional
    public FoodDtos.ResultImport importAll(java.io.Reader file,
                                                     DataSource source,
                                                     char separator) throws java.io.IOException {
        Long accountId = contextCurrent.accountId();
        var result = reader.read(file, source == null ? DataSource.CUSTOM : source, separator);

        if (result.empty()) {
            return new FoodDtos.ResultImport(0, result.rowsIgnored(), result.warnings());
        }

        var warnings = new java.util.ArrayList<>(result.warnings());
        var store = new java.util.ArrayList<Food>(result.foods().size());
        var codesSeen = new java.util.HashSet<String>();
        int duplicated = 0;

        for (Food food : result.foods()) {
            // A mandatory link: the imported food belongs to whoever imported it.
            food.setAccountId(accountId);

            String key = food.getCodeBarcode() != null
                    ? food.getCodeBarcode()
                    : food.getCodeSource();
            if (key != null && !codesSeen.add(key)) {
                duplicated++;
                continue;
            }
            store.add(food);
        }
        if (duplicated > 0) {
            warnings.add("%d linhas com código repetido foram descartadas.".formatted(duplicated));
        }

        foodRepository.saveAll(store);
        log.info("Importação de tabela: conta={} fonte={} gravados={}", accountId, source, store.size());

        return new FoodDtos.ResultImport(
                store.size(), result.rowsIgnored() + duplicated, warnings);
    }

    @Transactional
    public FoodDtos.Detail create(FoodDtos.FoodRequest req) {
        Food food = new Food(req.description(), DataSource.CUSTOM);
        food.setAccountId(contextCurrent.accountId());
        apply(req, food);

        foodRepository.save(food);
        log.info("Alimento próprio cadastrado: id={} conta={}", food.getId(), food.getAccountId());
        return FoodDtos.Detail.from(food, food.getMeasures(), food.getAccountId());
    }

    @Transactional
    public FoodDtos.Detail update(Long id, FoodDtos.FoodRequest req) {
        Food food = visibleRequire(id);
        if (food.isPublicBase()) {
            throw new BusinessRuleException(
                    "Alimentos das tabelas de referência não podem ser editados. "
                    + "Cadastre um alimento próprio a partir dele, se precisar ajustar valores.");
        }
        food.setDescription(req.description());
        apply(req, food);
        return FoodDtos.Detail.from(food, food.getMeasures(), food.getAccountId());
    }

    @Transactional
    public void deactivate(Long id) {
        Food food = visibleRequire(id);
        if (food.isPublicBase()) {
            throw new BusinessRuleException("Alimentos das tabelas de referência não podem ser removidos");
        }
        food.setActive(false);
    }

    private void apply(FoodDtos.FoodRequest req, Food food) {
        food.setGroup(req.group());
        // Digits only: the code may arrive punctuated from the reader or typed by hand.
        food.setCodeBarcode(req.codeBarcode() == null ? null
                : req.codeBarcode().replaceAll("[^0-9]", ""));
        food.setBrand(req.brand());
        food.setComposition(req.composition().toDomain());

        food.getMeasures().clear();
        if (req.measures() != null) {
            long standards = req.measures().stream().filter(FoodDtos.MeasureRequest::standard).count();
            if (standards > 1) {
                throw new BusinessRuleException("Apenas uma medida caseira pode ser marcada como padrão");
            }
            req.measures().forEach(m -> {
                var measure = new HouseholdMeasure(m.description(), m.grams());
                measure.setStandard(m.standard());
                // Without the accountId the portion would be read as base catalog and
                // the food's own owner would not be able to edit it later.
                measure.setAccountId(food.getAccountId());
                food.addMeasure(measure);
            });
        }
    }
}
