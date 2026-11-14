-- T9.4 actual SELECT, executed on new ticketing_invariants DB in project ticketing-audit-e28927563c4c.
-- Read-only; do not substitute the demo database. Rerun the WorkingTree gate for a fresh reproduction.
SELECT jsonb_build_object(
 'migrations',(SELECT jsonb_agg(jsonb_build_object('version',version,'checksum',checksum,'success',success,'installedOn',installed_on) ORDER BY installed_rank) FROM public.flyway_schema_history),
 'tables',(SELECT count(*) FROM information_schema.tables WHERE table_schema='ticketing' AND table_type='BASE TABLE'),
 'users',(SELECT count(*) FROM ticketing.users), 'events',(SELECT count(*) FROM ticketing.events),
 'gaCapacity',(SELECT sum(capacity) FROM ticketing.inventory_ga), 'seats',(SELECT count(*) FROM ticketing.seats),
 'bookings',(SELECT count(*) FROM ticketing.bookings));