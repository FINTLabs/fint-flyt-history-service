-- Stands in for the instance-service schema, which local-staging names explicitly. Empty: JPA startup only needs the
-- schema name; deletion after dispatch finds no instances locally.
create schema if not exists fintlabs_no_instance authorization postgres;
