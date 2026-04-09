-- ============================================
-- SQL Statement لتحديث قاعدة البيانات
-- قم بتشغيل هذا الـ SQL في قاعدة البيانات الخاصة بك
-- ============================================

-- 1. تحديث اسم المجموعة من "Default Segment" إلى "DefaultSegment"
UPDATE system_settings 
SET setting_group = 'DefaultSegment' 
WHERE setting_group = 'Default Segment';

-- 2. حذف إعداد information_segmentation_enabled (لم يعد مطلوباً)
DELETE FROM system_settings 
WHERE setting_group = 'DefaultSegment' 
AND setting_key = 'information_segmentation_enabled';

-- 3. التأكد من وجود enterprise_segment_default (سيحتفظ بالقيمة الحالية إن وجدت)
INSERT INTO system_settings (setting_group, setting_key, setting_value, data_type)
VALUES ('DefaultSegment', 'enterprise_segment_default', 'false', 'boolean')
ON DUPLICATE KEY UPDATE setting_value = setting_value;

-- 4. التأكد من وجود assigned_segments_default (سيحتفظ بالقيمة الحالية إن وجدت)
INSERT INTO system_settings (setting_group, setting_key, setting_value, data_type)
VALUES ('DefaultSegment', 'assigned_segments_default', 'false', 'boolean')
ON DUPLICATE KEY UPDATE setting_value = setting_value;

