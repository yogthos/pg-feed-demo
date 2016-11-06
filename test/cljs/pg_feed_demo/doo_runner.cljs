(ns pg-feed-demo.doo-runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [pg-feed-demo.core-test]))

(doo-tests 'pg-feed-demo.core-test)

