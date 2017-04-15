CREATE TABLE events
(id SERIAL PRIMARY KEY,
 event TEXT);
--;;
CREATE FUNCTION notify_trigger() RETURNS trigger AS $$
DECLARE
BEGIN
 -- TG_TABLE_NAME - name of the table that was triggered
 -- TG_OP - name of the trigger operation
 -- NEW - the new value in the row
 -- OLD - the old value in the row
 IF TG_OP = 'INSERT' or TG_OP = 'UPDATE' THEN
   execute 'NOTIFY '
   || TG_TABLE_NAME
   || ', '''
   || TG_OP
   || ' '
   || NEW
   || '''';
 ELSE
   execute 'NOTIFY '
   || TG_TABLE_NAME
   || ', '''
   || TG_OP
   || ' '
   || OLD
   || '''';
 END IF;
 return new;
END;
$$ LANGUAGE plpgsql;
--;;
CREATE TRIGGER event_trigger
AFTER INSERT or UPDATE or DELETE ON events
FOR EACH ROW EXECUTE PROCEDURE notify_trigger();
