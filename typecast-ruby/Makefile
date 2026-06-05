.PHONY: install test coverage e2e

install:
	bundle install --path vendor/bundle

test:
	bundle exec ruby -Itest test/all_test.rb

coverage:
	bundle exec ruby -Itest test/all_test.rb

e2e:
	bundle exec ruby -Itest test/e2e_test.rb
