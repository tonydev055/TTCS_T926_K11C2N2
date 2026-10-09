BEGIN;
-- S3-08: tìm đại lý không phân biệt hoa/thường và dấu tiếng Việt. Chạy sau sprint3-customer-lock.sql.
CREATE OR REPLACE FUNCTION khongdau(t text) RETURNS text
LANGUAGE sql IMMUTABLE AS $$
  SELECT translate(lower(coalesce(t, '')),
    'àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ',
    'aaaaaaaaaaaaaaaaaeeeeeeeeeeeiiiiiooooooooooooooooouuuuuuuuuuuyyyyyd')
$$;
COMMIT;
