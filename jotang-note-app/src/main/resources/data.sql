-- Jotang Note MVP 基础数据，对应《概要设计》§7
-- 幂等：可随应用启动重复执行

-- 内置「其他」课程（上传时无对应课程可选的兜底项）
-- D8 起 course 不再有 college 列，学院挂在上传者身上（概要设计 §3.3）
INSERT INTO `course` (`name`, `is_other`)
SELECT '其他', 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `course` WHERE `is_other` = 1);

-- 课程种子数据。这里的书写顺序 = 下拉框的展示顺序（CourseService 按 id 升序输出）。
-- name 上有 uk_name 唯一约束，靠 INSERT IGNORE 保证可重复执行。
-- is_other 走列默认值 0；内置「其他」由上面那条单独维护，不列入此处。
INSERT IGNORE INTO `course` (`name`) VALUES
  ('微积分'),
  ('线性代数'),
  ('c语言'),
  ('大学物理'),
  ('军事理论'),
  ('离散数学'),
  ('编译原理'),
  ('计算机系统结构'),
  ('概率论');

-- 标签种子数据，按需增删
INSERT IGNORE INTO `tag` (`name`) VALUES
  ('期末复习'),
  ('课堂笔记'),
  ('习题解答'),
  ('实验报告');
