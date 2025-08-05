-- Assumption: Concurrent modifications to the same user_id are extremely rare
-- This is realistic for most web applications where:
-- 1. User registration/activation happens once
-- 2. User deletion/banning is infrequent administrative action
-- 3. State transitions are typically single-threaded per user

CREATE OR REPLACE FUNCTION user_state_integrity_trigger()
    RETURNS TRIGGER AS
$$
BEGIN
    -- Simple existence checks without advisory locks
    -- Acceptable risk given the "extremely rare" assumption

    IF TG_TABLE_NAME = 'active_users'
    THEN
        IF EXISTS (SELECT 1 FROM pending_users WHERE user_id = NEW.user_id)
        THEN
            RAISE EXCEPTION 'Integrity violation: User % already pending (active_users)', NEW.user_id;
        END IF;
        IF EXISTS (SELECT 1 FROM deleted_users WHERE user_id = NEW.user_id)
        THEN
            RAISE EXCEPTION 'Integrity violation: User % already deleted (active_users)', NEW.user_id;
        END IF;

    ELSIF TG_TABLE_NAME = 'pending_users'
    THEN
        IF EXISTS (SELECT 1 FROM active_users WHERE user_id = NEW.user_id)
        THEN
            RAISE EXCEPTION 'Integrity violation: User % already active (pending_users)', NEW.user_id;
        END IF;
        IF EXISTS (SELECT 1 FROM deleted_users WHERE user_id = NEW.user_id)
        THEN
            RAISE EXCEPTION 'Integrity violation: User % already deleted (pending_users)', NEW.user_id;
        END IF;

    ELSIF TG_TABLE_NAME = 'deleted_users'
    THEN
        IF EXISTS (SELECT 1 FROM active_users WHERE user_id = NEW.user_id)
        THEN
            RAISE EXCEPTION 'Integrity violation: User % already active (deleted_users)', NEW.user_id;
        END IF;
        IF EXISTS (SELECT 1 FROM pending_users WHERE user_id = NEW.user_id)
        THEN
            RAISE EXCEPTION 'Integrity violation: User % already pending (deleted_users)', NEW.user_id;
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply triggers
CREATE TRIGGER enforce_active_user_state
    BEFORE INSERT
    ON active_users
    FOR EACH ROW
EXECUTE FUNCTION user_state_integrity_trigger();

CREATE TRIGGER enforce_pending_user_state
    BEFORE INSERT
    ON pending_users
    FOR EACH ROW
EXECUTE FUNCTION user_state_integrity_trigger();

CREATE TRIGGER enforce_deleted_user_state
    BEFORE INSERT
    ON deleted_users
    FOR EACH ROW
EXECUTE FUNCTION user_state_integrity_trigger();