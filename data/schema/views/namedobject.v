CREATE VIEW namedobject_v
AS
SELECT n.dbkey        dbkey,
       n.name         name,
       n.description  description,
       n.markup       markup,
       n.files        files,
       n.lastmodified lastmodified,
       d.objecttype   objecttype,
       d.datecreated  datecreated,
       d.properties   properties,
       d.id           id,
       d.version      version,
       d.latest       latest,
       n.userobjecttypedbkey userobjecttypedbkey
FROM   namedobject n,
       dataobject  d
WHERE  d.dbkey = n.dbkey
