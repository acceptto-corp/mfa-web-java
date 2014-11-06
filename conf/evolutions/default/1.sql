# --- Created by Ebean DDL
# To stop Ebean DDL generation, remove this comment and start using Evolutions

# --- !Ups

create table local_user (
  id                        bigint not null,
  email                     varchar(255),
  fullname                  varchar(255),
  confirmation_token        varchar(255),
  password_hash             varchar(255),
  date_creation             timestamp,
  validated                 boolean,
  mfa_access_token          varchar(255),
  mfa_authenticated         boolean,
  constraint uq_local_user_email unique (email),
  constraint uq_local_user_fullname unique (fullname),
  constraint pk_local_user primary key (id))
;

create table token (
  token                     varchar(255) not null,
  user_id                   bigint,
  type                      varchar(8),
  date_creation             timestamp,
  email                     varchar(255),
  constraint ck_token_type check (type in ('password','email')),
  constraint pk_token primary key (token))
;

create sequence local_user_seq;

create sequence token_seq;




# --- !Downs

drop table if exists local_user cascade;

drop table if exists token cascade;

drop sequence if exists local_user_seq;

drop sequence if exists token_seq;

