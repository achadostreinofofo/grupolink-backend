-- Regras de agendamento por estrutura (só afetam mensagens agendadas).
-- Grade de horários: de schedule_window_start a schedule_window_end, a cada
-- schedule_interval_minutes. Defaults: 08:00–18:00, intervalo de 5 minutos.
ALTER TABLE structures
    ADD COLUMN schedule_window_start     TIME    NOT NULL DEFAULT '08:00:00',
    ADD COLUMN schedule_window_end       TIME    NOT NULL DEFAULT '18:00:00',
    ADD COLUMN schedule_interval_minutes INTEGER NOT NULL DEFAULT 5;
