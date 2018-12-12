# --- Created by Ebean DDL
# To stop Ebean DDL generation, remove this comment and start using Evolutions

# --- !Ups

create table local_user (
  id                        integer primary key AUTOINCREMENT,
  email                     varchar(255),
  fullname                  varchar(255),
  confirmation_token        varchar(255),
  password_hash             varchar(255),
  date_creation             timestamp,
  validated                 integer(1),
  mfa_email                 varchar(255),
  mfa_authenticated         integer(1),
  constraint uq_local_user_email unique (email),
  constraint uq_local_user_fullname unique (fullname))
;

create table token (
  token                     varchar(255) primary key,
  user_id                   integer,
  type                      varchar(8),
  date_creation             timestamp,
  email                     varchar(255),
  constraint ck_token_type check (type in ('password','email')))
;




# --- !Downs

PRAGMA foreign_keys = OFF;

drop table local_user;

drop table token;

PRAGMA foreign_keys = ON;

