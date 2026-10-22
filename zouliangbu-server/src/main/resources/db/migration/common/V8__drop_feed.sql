-- V8：下线「首页动态」模块。
--
-- 背景：App 首页改为纯本地轨迹时间轴，不再有同城动态信息流。
-- 该模块的内容必须依赖服务端，断网即空，与「户外默认没信号」的产品前提冲突
-- （见 docs/product-philosophy.md：社交层 0 投入）。
--
-- 注意：V1/V2/V4/V5 涉及 feed_post 的脚本已在线执行过，**不可修改**，
-- 这里只做向前删除。审计表 app_config 中的 feed.* 历史记录同样按"审计不删"原则保留。

DROP TABLE IF EXISTS feed_post;
