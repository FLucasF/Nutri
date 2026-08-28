package br.com.nutriplan.alimento.repository;

import br.com.nutriplan.alimento.domain.Alimento;
import br.com.nutriplan.alimento.domain.FonteDeDados;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AlimentoRepository extends JpaRepository<Alimento, Long> {

    boolean existsByFonte(FonteDeDados fonte);

    /**
     * Busca no acervo visivel para a conta: as bases publicas (conta_id nulo)
     * mais os alimentos cadastrados pelo proprio consultorio.
     *
     * O termo e comparado contra descricao_busca, ja sem acentos e em
     * minusculas, para que "acucar" encontre "Acucar".
     */
    @Query("""
           select a from Alimento a
           where a.ativo = true
             and (a.contaId is null or a.contaId = :contaId)
             and (:termo is null or a.descricaoBusca like concat('%', :termo, '%'))
             and (:grupo is null or a.grupo = :grupo)
             and (:fonte is null or a.fonte = :fonte)
           """)
    Page<Alimento> buscar(@Param("contaId") Long contaId,
                          @Param("termo") String termo,
                          @Param("grupo") String grupo,
                          @Param("fonte") FonteDeDados fonte,
                          Pageable pageable);

    /**
     * Busca por termo, ordenada por relevância clínica.
     *
     * Sem ordenação por relevância a base fica inutilizável: são 21 mil
     * industrializados contra 597 alimentos de referência, e a ordem alfabética
     * faz "banana" devolver "&Joy Frutas Banana + Cacau" antes da fruta.
     *
     * Três critérios, nesta ordem:
     *
     * 1. **Procedência.** TACO primeiro, depois TBCA e IBGE, e por último o
     *    produto de fabricante. Quem digita "leite" quer leite, não alfajor de
     *    doce de leite. A TACO vem à frente do IBGE porque caracteriza o
     *    alimento básico; o IBGE complementa com preparações, que interessam
     *    quando o básico não responde — "feijoada" não existe na TACO.
     *
     * 2. **Como o termo casou.** O teste é por *palavra inteira*, não por
     *    prefixo: sem isso "arroz" devolve "Arrozina" — onde as cinco letras
     *    são só o começo de outra palavra — antes do arroz comum. A ordem é:
     *    nome idêntico, termo abrindo o nome, termo como palavra no meio, e só
     *    então termo colado dentro de outra palavra.
     *
     * 3. **Tamanho do nome.** "Arroz, tipo 1, cozido" antes de "Arroz de forno
     *    com frango e creme de milho" — nome curto costuma ser o genérico.
     *
     * O termo já chega normalizado, sem acento e em minúsculas.
     */
    @Query("""
           select a from Alimento a
           where a.ativo = true
             and (a.contaId is null or a.contaId = :contaId)
             and a.descricaoBusca like concat('%', :termo, '%')
             and (:grupo is null or a.grupo = :grupo)
             and (:fonte is null or a.fonte = :fonte)
           order by
             case a.fonte
                  when br.com.nutriplan.alimento.domain.FonteDeDados.TACO then 0
                  when br.com.nutriplan.alimento.domain.FonteDeDados.TBCA then 1
                  when br.com.nutriplan.alimento.domain.FonteDeDados.IBGE then 2
                  else 3 end,
             case when a.descricaoBusca = :termo then 0
                  when a.descricaoBusca like concat(:termo, ' %')  then 1
                  when a.descricaoBusca like concat(:termo, ',%')  then 1
                  when a.descricaoBusca like concat('% ', :termo)       then 2
                  when a.descricaoBusca like concat('% ', :termo, ' %') then 2
                  when a.descricaoBusca like concat('% ', :termo, ',%') then 2
                  when a.descricaoBusca like concat(:termo, '%') then 3
                  else 4 end,
             length(a.descricao),
             a.descricao
           """)
    Page<Alimento> buscarPorRelevancia(@Param("contaId") Long contaId,
                                       @Param("termo") String termo,
                                       @Param("grupo") String grupo,
                                       @Param("fonte") FonteDeDados fonte,
                                       Pageable pageable);

    /**
     * Localiza o produto pelo codigo de barras, dentro do acervo visivel.
     *
     * Devolve lista, e nao um unico registro, porque o codigo nao e unico na
     * base: o mesmo EAN pode existir no acervo publico e no cadastro proprio do
     * consultorio, e ha duplicidade dentro do proprio Open Food Facts, que e
     * colaborativo. A ordenacao poe o alimento do consultorio na frente — quem
     * cadastrou o proprio produto quer ver o proprio produto.
     */
    @Query("""
           select a from Alimento a
           where a.ativo = true
             and a.codigoBarras = :codigo
             and (a.contaId is null or a.contaId = :contaId)
           order by case when a.contaId is null then 1 else 0 end, a.id
           """)
    List<Alimento> porCodigoDeBarras(@Param("codigo") String codigo,
                                     @Param("contaId") Long contaId);

    /** Carrega o alimento apenas se for publico ou da propria conta. */
    @Query("""
           select a from Alimento a
           where a.id = :id
             and (a.contaId is null or a.contaId = :contaId)
           """)
    Optional<Alimento> buscarVisivel(@Param("id") Long id, @Param("contaId") Long contaId);

    /** Carrega vários de uma vez, para montar uma refeicao sem N+1 consultas. */
    @Query("""
           select a from Alimento a
           where a.id in :ids
             and (a.contaId is null or a.contaId = :contaId)
           """)
    List<Alimento> buscarVisiveis(@Param("ids") List<Long> ids, @Param("contaId") Long contaId);

    /**
     * Receitas do consultorio. Nunca traz receita de outra conta, e nunca traz
     * as bases publicas: receita e sempre autoral.
     */
    @Query("""
           select a from Alimento a
           where a.ativo = true
             and a.fonte = br.com.nutriplan.alimento.domain.FonteDeDados.RECEITA
             and a.contaId = :contaId
             and (:termo is null or a.descricaoBusca like concat('%', :termo, '%'))
           order by a.descricao
           """)
    Page<Alimento> buscarReceitas(@Param("contaId") Long contaId,
                                  @Param("termo") String termo,
                                  Pageable pageable);

    @Query("select distinct a.grupo from Alimento a where a.grupo is not null order by a.grupo")
    List<String> listarGrupos();

    /** Usado pelo importador de medidas para casar codigo da tabela com o id gerado. */
    List<Alimento> findByFonte(FonteDeDados fonte);

    long countByFonte(FonteDeDados fonte);
}
