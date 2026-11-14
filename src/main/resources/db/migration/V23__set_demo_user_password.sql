-- 시연용 테스트 계정(demo@kattok.app)이 실제로 로그인할 수 있도록 비밀번호를 넣는다.
-- V22에서는 로그인 API가 없어 로그인할 수 없는 값('DEMO-NO-LOGIN')으로 만들어 두었다.
-- 테스트 계정 전용 비밀번호이며 실제 서비스 계정에 쓰지 않는다: kattok-demo-1b9e2b
update users
set password_hash = '$2y$10$46rHyOKT4.b6G0F9IQObfuYlzX5M7boQb/X9I06FiNE6i15QeEhYi',
	updated_at = now()
where email = 'demo@kattok.app';
