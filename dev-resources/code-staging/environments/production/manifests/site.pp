node default {
  notify {"hi there ${trusted['certname']}": }
  notify {"hi there ${environment}": }
  include 'apt'
  include 'ntp'
}
