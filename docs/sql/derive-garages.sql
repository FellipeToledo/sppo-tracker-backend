-- ⚠️ ESTA NÃO É A FONTE DOS POLÍGONOS. A fonte é o dataset oficial do data.rio
-- ("Garagens de operadores do SPPO"), empacotado em
-- `src/main/resources/geofences/garages.geojson` — 37 polígonos com nome do operador,
-- endereço e bairro, com os limites reais do lote.
--
-- Este SELECT é uma FERRAMENTA DE CONFERÊNCIA: reconstrói, a partir do histórico
-- público de GPS (`tipo_parada = 'garagem'`, a própria classificação da SMTR), os
-- lugares onde a frota de fato para. Serve para responder "a camada oficial cobre o
-- que a frota usa?" — e a resposta, medida em 2026-07-25, foi **não inteiramente**:
-- dos 35 aglomerados encontrados, 21 caem dentro de uma garagem oficial e 14 não
-- (o maior deles com 207 mil observações). Ver docs/estado-do-projeto.md §10.
--
-- Uso (SOMENTE SELECT; rode o dry-run antes — custo abaixo):
--   projeto de billing: um projeto seu (o dataset rj-smtr é público)
--   custo medido em 2026-07-25: 5,66 GiB varridos ≈ US$ 0,028 (30 dias de partição)
--   saída: 35 aglomerados, 482 vértices em convex hulls
--
-- Parâmetros e por quê:
--   ROUND(lat/lon, 4)  -> célula de ~11 m: colapsa milhões de pontos em milhares de células
--   HAVING n >= 10     -> descarta célula com passagem esporádica (ônibus só cruzando)
--   DBSCAN(80, 5)      -> 80 m de raio, mínimo 5 células: junta o pátio inteiro sem
--                         emendar garagens vizinhas
--   pontos >= 1000     -> descarta aglomerado ocasional (não é garagem)
--   área 1e3..3e5 m²   -> piso remove sliver; teto remove o encadeamento do DBSCAN ao
--                         longo de via (um cluster de 8 km² apareceu na Baixada sem ele)
--   bbox do Rio        -> mesmo box de OUT_OF_MUNICIPALITY, que tem precedência sobre
--                         IN_GARAGE (§4.2) — garagem fora do município seria inócua
--
-- ⚠️ A geometria daqui é CONVEX HULL dos pontos observados — generosa e sem concavidade.
-- É boa para *localizar* onde a frota para, não para virar geofence: o polígono oficial
-- do lote é mais fiel. Por isso este arquivo não alimenta a aplicação.
WITH pts AS (
  SELECT ROUND(latitude, 4) AS la, ROUND(longitude, 4) AS lo, COUNT(*) AS n
  FROM `rj-smtr.br_rj_riodejaneiro_veiculos.gps_sppo`
  WHERE data BETWEEN DATE "2026-03-31" AND DATE "2026-04-29"
    AND tipo_parada = "garagem"
    AND latitude BETWEEN -23.10 AND -22.70
    AND longitude BETWEEN -43.80 AND -43.05
  GROUP BY la, lo
  HAVING n >= 10
),
clustered AS (
  SELECT la, lo, n, ST_CLUSTERDBSCAN(ST_GEOGPOINT(lo, la), 80, 5) OVER () AS cid
  FROM pts
),
hulls AS (
  SELECT cid,
         SUM(n) AS pontos,
         COUNT(*) AS celulas,
         ST_CONVEXHULL(ST_UNION_AGG(ST_GEOGPOINT(lo, la))) AS hull
  FROM clustered
  WHERE cid IS NOT NULL
  GROUP BY cid
)
SELECT cid, pontos, celulas,
       ROUND(ST_AREA(hull)) AS area_m2,
       ROUND(ST_Y(ST_CENTROID(hull)), 5) AS lat,
       ROUND(ST_X(ST_CENTROID(hull)), 5) AS lon,
       ST_ASGEOJSON(hull) AS geom
FROM hulls
WHERE pontos >= 1000 AND ST_AREA(hull) BETWEEN 1000 AND 300000
ORDER BY pontos DESC
