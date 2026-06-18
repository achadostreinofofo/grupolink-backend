-- Marca o instante da última troca de senha. Tokens JWT emitidos antes deste
-- instante são considerados inválidos (invalida sessões antigas após reset).
ALTER TABLE users ADD COLUMN password_changed_at TIMESTAMP;
