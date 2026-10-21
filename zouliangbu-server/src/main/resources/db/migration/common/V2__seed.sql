-- V2: 种子数据（文案与 App 侧 data/mock/MockData.kt 保持一致，便于对照联调）

INSERT INTO feed_post (title, author, like_count, city, cover_url, cover_aspect_ratio, status) VALUES
('武功山反穿：两天一夜看云海日出全记录', '山野拾光', 128, '萍乡', NULL, 1.25, 1),
('莫干山小环线，亲子徒步初体验', '竹间漫步', 46, '湖州', NULL, 1.00, 1),
('雨崩村徒步第三天，神瀑下面的风太大了', '行走的高原', 302, '迪庆', NULL, 0.85, 1),
('香山红叶快到峰值了，周末冲了一条冷门线', '西山客', 88, '北京', NULL, 1.15, 1),
('虎跳峡高路徒步：一条值得走第二遍的线', '峡谷行者', 215, '丽江', NULL, 1.30, 1),
('新手第一次重装，徽杭古道 18km 打卡', '青弋江畔', 57, '宣城', NULL, 0.90, 1),
('梧桐山夜爬看日出，凌晨四点的深圳', '鹏城夜徒', 173, '深圳', NULL, 1.05, 1),
('武功山帐篷节人从众，但云海值了', '云中草原', 96, '萍乡', NULL, 1.20, 1),
('四姑娘山大峰登顶，人生第一座 5000+', '雪线之上', 421, '阿坝', NULL, 0.95, 1),
('武功山—明月山穿越，三天两晚行程贴', '赣西驴友', 64, '宜春', NULL, 1.10, 1);

INSERT INTO route_section (title, subtitle, sort_weight) VALUES
('口碑山野线', '走过的人都说好', 30),
('新手友好线', '难度低、补给全，第一次徒步选这里', 20),
('进阶挑战线', '多日重装 / 高海拔，量力而行', 10);

-- 推荐位（section_id 为 NULL，只出现在横向推荐）
INSERT INTO route (name, region, distance_km, ascent_meters, tags, section_id, is_recommend, sort_weight, status) VALUES
('武功山反穿线', '萍乡', 13.5, 1200, '户外毕业线,露营', NULL, TRUE, 100, 1),
('雨崩 inner 环线', '迪庆', 26.0, 1600, '高原,多日', NULL, TRUE, 90, 1),
('长穿毕', '阿坝', 46.0, 2100, '重装,经典', NULL, TRUE, 80, 1);

-- 分组线路
INSERT INTO route (name, region, distance_km, ascent_meters, tags, section_id, is_recommend, sort_weight, status) VALUES
('鹏城十峰·梧桐山', '深圳', 9.8, 940, '夜爬,看日出', 1, FALSE, 100, 1),
('惠州大南山', '惠州', 13.6, 1050, '草甸,一日往返', 1, FALSE, 90, 1),
('莫干山蒋公道', '湖州', 11.2, 520, '亲子,竹林', 1, FALSE, 80, 1),
('徽杭古道', '宣城', 18.0, 680, '古道,新手友好', 1, FALSE, 70, 1),
('莫干山岭上环线', '湖州', 7.6, 430, '周末,轻装', 2, FALSE, 100, 1),
('漓江徒步精华段', '桂林', 15.5, 350, '山水,新手友好', 2, FALSE, 90, 1),
('哈巴雪山', '香格里拉', 21.0, 2900, '雪山,进阶', 3, FALSE, 100, 1),
('大五台顺穿', '忻州', 52.0, 2300, '朝台,多日', 3, FALSE, 90, 1),
('小五台北东线', '张家口', 28.0, 1750, '华北K2,进阶', 3, FALSE, 80, 1);

INSERT INTO gear_category (code, name, note, icon_key, sort_weight) VALUES
('g1', '背包', '一日 / 多日背负', 'backpack', 10),
('g2', '登山鞋', '越野 / 重装', 'hiking', 20),
('g3', '冲锋衣', '防风防雨', 'checkroom', 30),
('g4', '登山杖', '省膝盖 30%', 'hiking', 40),
('g5', '帐篷睡袋', '露营过夜', 'cabin', 50),
('g6', '头灯照明', '夜爬必备', 'flashlight', 60),
('g7', '水具滤水', '户外补水', 'drink', 70),
('g8', '防晒配件', '帽子 / 冰袖', 'sunny', 80);
