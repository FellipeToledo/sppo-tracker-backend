-- Deriva os polígonos de garagem (GARAGE_GEOFENCE, docs/regras-de-negocio.md §4.3) a
-- partir do histórico público de GPS da SMTR, usando a própria classificação da SMTR
-- (`tipo_parada = 'garagem'`) como semente. Gera o `src/main/resources/garages.geojson`.
--
-- Por que assim: a camada oficial `rj-smtr.br_rj_riodejaneiro_geo.garagem_polygon` é uma
-- VIEW sobre `rj-smtr-staging`, que não é pública (Access Denied). Este SELECT reconstrói
-- os polígonos a partir dos pontos que a SMTR já rotulou como "em garagem".
--
-- Uso (SOMENTE SELECT; rode o dry-run antes — custo abaixo):
--   projeto de billing: um projeto seu (o dataset rj-smtr é público)
--   custo medido em 2026-07-25: 5,66 GiB varridos ≈ US$ 0,028 (30 dias de partição)
--   saída: 35 polígonos, 482 vértices, ~36 KB de GeoJSON
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
-- ⚠️ A geometria é o CONVEX HULL dos pontos observados: cobre o pátio de forma um pouco
-- generosa e pode fechar concavidades (pátio em L). Para apertar, trocar por união de
-- buffers das células — mais fiel, porém com muito mais vértices.
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
