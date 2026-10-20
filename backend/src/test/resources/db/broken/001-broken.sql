--liquibase formatted sql

--changeset test:999-deliberately-broken
CREATE TABLE this_is_not_valid_sql (;
