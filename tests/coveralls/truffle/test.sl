function uncalled() {
  i = 0;
  if (false) {
    sum = 0;
  }
}

function called() {
  sum = 0;

  while (sum < 5) {
    sum = sum + 1;
  } 

  return sum;
}

function main() {
  i = 0;

  while (i < 20) {
    value = called();
    i = i + 1;
    if (i > 20) {
      println("never executed");
    }
  }
}
