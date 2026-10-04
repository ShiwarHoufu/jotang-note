-- Jotang Note MVP 基础数据，对应《概要设计》§7
-- 幂等：可随应用启动重复执行

-- 内置「其他」课程（上传时无对应课程可选的兜底项）
INSERT INTO `course` (`name`, `college`, `is_other`)
SELECT '其他', NULL, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `course` WHERE `is_other` = 1);

-- 标签种子数据，按需增删
INSERT IGNORE INTO `tag` (`name`) VALUES
  ('期末复习'),
  ('课堂笔记'),
  ('习题解答'),
  ('实验报告');
