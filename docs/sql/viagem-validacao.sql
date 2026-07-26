-- Investigação da §11 (validação de viagem por segmento) — 2026-07-26.
--
-- CONCLUSÃO: a SMTR já publica a validação por segmento pronta em
-- `rj-smtr.monitoramento.viagem_validacao`. Não recalculamos: consumimos.
-- Ver docs/regras-de-negocio.md §11 e docs/estado-do-projeto.md §11.
--
-- SOMENTE SELECT. Rode o dry-run antes de cada uma; o custo medido está anotado.
-- Projeto de billing: um projeto seu (o dataset rj-smtr é público). A conta usada
-- aqui foi a service account do gtfs-service (api-gtfs-smtr@portal-da-mobilidade-rj).
--
-- ⚠️ ARMADILHA: `modo` é 'Ônibus SPPO', NÃO 'Ônibus'. O filtro errado devolve
-- zero linhas sem erro nenhum. Se o acento atrapalhar (shell, wrapper .cmd),
-- use `modo LIKE '%SPPO%'`.

-- ---------------------------------------------------------------------------
-- 1) Cobertura, latência e modos disponíveis.  Custo medido: 60 MB.
-- ---------------------------------------------------------------------------
SELECT
  modo,
  COUNT(*) AS viagens,
  MIN(data) AS primeira_data,
  MAX(data) AS ultima_data,                      -- latência: D-3 em 2026-07-26
  MAX(datetime_ultima_atualizacao) AS ultima_atualizacao
FROM `rj-smtr.monitoramento.viagem_validacao`
WHERE data >= DATE_SUB(CURRENT_DATE('America/Sao_Paulo'), INTERVAL 60 DAY)
GROUP BY modo
ORDER BY viagens DESC;

-- ---------------------------------------------------------------------------
-- 2) Caracterização de um dia (grão, volume, qualidade).  Custo medido: 4 MB.
--    Resultado em 2026-07-23: 42.526 viagens · 428 serviços · 3.599 veículos ·
--    98,8% trajeto válido · índice médio 0,9927.
-- ---------------------------------------------------------------------------
SELECT
  COUNT(*) AS viagens,
  COUNT(DISTINCT servico) AS servicos,
  COUNT(DISTINCT id_veiculo) AS veiculos,
  COUNTIF(indicador_viagem_valida) AS viagem_valida,
  COUNTIF(indicador_trajeto_valido) AS trajeto_valido,
  ROUND(AVG(indice_validacao), 4) AS indice_medio,
  ROUND(AVG(quantidade_segmentos_considerados), 1) AS considerados_medio,
  ROUND(AVG(quantidade_segmentos_validos), 1) AS validos_medio,
  ROUND(AVG(CAST(quantidade_segmentos_necessarios AS FLOAT64)), 2) AS necessarios_medio,
  COUNTIF(NOT indicador_primeiro_segmento_valido) AS sem_primeiro_segmento,
  COUNTIF(NOT indicador_ultimo_segmento_valido) AS sem_ultimo_segmento,
  COUNTIF(indicador_servico_divergente) AS servico_divergente,
  COUNTIF(indicador_shape_invalido) AS shape_invalido
FROM `rj-smtr.monitoramento.viagem_validacao`
WHERE data = DATE '2026-07-23'
  AND modo LIKE '%SPPO%';

-- ---------------------------------------------------------------------------
-- 3) O limiar `quantidade_segmentos_necessarios` NÃO é derivável por fórmula.
--    Custo medido: 2 MB.  Aderência de cada candidata (42.526 viagens, 07-23):
--      floor(0,9c) 64,4% · ceil(0,9c) 45,4% · c-2 38,8% · c-1 26,6% ·
--      ceil(0,8c) 8,3% · round(0,8c) 6,4% · floor(0,8c) 3,0% · n>=c 0,0%
--    Por isso o campo é LIDO, nunca reproduzido (regra da §9: nenhum limiar
--    sem origem rastreável — foi o erro do corredor de ±15 m).
-- ---------------------------------------------------------------------------
WITH v AS (
  SELECT
    quantidade_segmentos_considerados AS c,
    CAST(quantidade_segmentos_necessarios AS INT64) AS n
  FROM `rj-smtr.monitoramento.viagem_validacao`
  WHERE data = DATE '2026-07-23'
    AND modo LIKE '%SPPO%'
    AND quantidade_segmentos_considerados IS NOT NULL
    AND quantidade_segmentos_necessarios IS NOT NULL
)
SELECT
  COUNT(*) AS viagens,
  ROUND(100 * COUNTIF(n = c - 1) / COUNT(*), 1) AS pct_c_menos_1,
  ROUND(100 * COUNTIF(n = c - 2) / COUNT(*), 1) AS pct_c_menos_2,
  ROUND(100 * COUNTIF(n = CAST(CEIL (0.8 * c) AS INT64)) / COUNT(*), 1) AS pct_ceil_80,
  ROUND(100 * COUNTIF(n = CAST(FLOOR(0.8 * c) AS INT64)) / COUNT(*), 1) AS pct_floor_80,
  ROUND(100 * COUNTIF(n = CAST(ROUND(0.8 * c) AS INT64)) / COUNT(*), 1) AS pct_round_80,
  ROUND(100 * COUNTIF(n = CAST(CEIL (0.9 * c) AS INT64)) / COUNT(*), 1) AS pct_ceil_90,
  ROUND(100 * COUNTIF(n = CAST(FLOOR(0.9 * c) AS INT64)) / COUNT(*), 1) AS pct_floor_90,
  ROUND(100 * COUNTIF(n >= c) / COUNT(*), 1) AS pct_n_maior_igual_c
FROM v;

-- ---------------------------------------------------------------------------
-- 4) Dimensionamento do buffer tratado em `planejamento.segmento_shape`.
--    Mantida como REGISTRO do custo que NÃO vamos pagar (a validação vem
--    pronta da SMTR): 119,0 KB/shape · 160,8 MB · 4,54 M vértices.
--
--    ⚠️ CUSTO — 21× de diferença por causa de uma linha:
--       WHERE feed_start_date = DATE '2026-07-19'                ->   229 MB
--       WHERE feed_start_date = (SELECT MAX(...) FROM a_tabela)  -> 4,92 GB
--    A subquery mata o partition pruning: o BigQuery varre os 50 feeds.
--    Descubra o feed vigente ANTES, com a query barata abaixo (13,7 MB), e
--    cole o literal. Medido em 2026-07-26.
--
--    O feed vigente é o de feed_start_date máximo (feed_end_date nulo) —
--    NÃO confundir com `feed_version`, que é o timestamp de publicação.
--
--      SELECT MAX(feed_start_date) FROM `rj-smtr.planejamento.segmento_shape`;
-- ---------------------------------------------------------------------------
WITH por_shape AS (
  SELECT
    shape_id,
    COUNT(*) AS n_seg,
    SUM(LENGTH(ST_ASTEXT(buffer))) AS b_tratado,
    SUM(LENGTH(ST_ASTEXT(buffer_completo))) AS b_completo,
    SUM(LENGTH(wkt_segmento)) AS seg_linha,
    SUM(LENGTH(ST_ASTEXT(buffer)) - LENGTH(REPLACE(ST_ASTEXT(buffer), ',', ''))) AS vertices
  FROM `rj-smtr.planejamento.segmento_shape`
  WHERE feed_start_date = DATE '2026-07-19'   -- feed vigente em 2026-07-26
  GROUP BY shape_id
)
SELECT
  COUNT(*) AS shapes,
  SUM(n_seg) AS segmentos,
  ROUND(SUM(seg_linha)  / 1048576, 1) AS mb_linhas_segmento,
  ROUND(SUM(b_completo) / 1048576, 1) AS mb_buffer_completo,
  ROUND(SUM(b_tratado)  / 1048576, 1) AS mb_buffer_tratado,
  ROUND(AVG(b_tratado) / 1024, 1) AS kb_tratado_por_shape,
  ROUND(APPROX_QUANTILES(b_tratado, 100)[OFFSET(95)] / 1024, 1) AS kb_tratado_p95,
  ROUND(MAX(b_tratado) / 1024, 1) AS kb_tratado_max,
  SUM(vertices) AS vertices_tratado_total
FROM por_shape;
