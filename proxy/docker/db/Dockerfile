FROM postgres:16

COPY init-app-db.sh /docker-entrypoint-initdb.d/
RUN sed -i 's/\r$//' /docker-entrypoint-initdb.d/init-app-db.sh
