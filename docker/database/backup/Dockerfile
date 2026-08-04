FROM postgres:16-alpine

COPY backup/backup-entrypoint.sh /usr/local/bin/backup-entrypoint.sh
COPY backup/backup-run.sh /usr/local/bin/backup-run.sh

RUN chmod 0644 /usr/local/bin/backup-entrypoint.sh
RUN chmod 0644 /usr/local/bin/backup-run.sh

CMD ["sh", "/usr/local/bin/backup-entrypoint.sh"]
