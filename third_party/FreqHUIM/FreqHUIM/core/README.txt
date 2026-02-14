the data file must be as follow: 
  * each line contains the transaction, -1, the sum of weight, -1, the weight of each item of the transaction, and 0
  * 0 can't be used for items

here a data example (example.data)

1 2 -1 5 -1 2 3 0
1 2 3 -1 5 -1 2 2 1 0
1 3 -1 5 -1 2 3 0
2 3 4 5 -1 13 -1 1 1 1 10 0
3 5 -1 2 -1 1 1 0



- use make to compile

- to execute, use:

./sat4fhuim -minutil=min_utility_value -minsupp=min_supp_value -closed=1 -verb=3 example.data 

example
=====
./sat4fhuim -minutil=5 -minsupp=2 -closed=1 -verb=3 example.data



remark
=====
- verbosity=3 to display models, < 3 if not.
