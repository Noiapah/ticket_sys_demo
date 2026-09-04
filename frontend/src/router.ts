import { createRouter, createWebHashHistory } from 'vue-router'
import DashboardView from './views/DashboardView.vue'
import NewTicketView from './views/NewTicketView.vue'
import TicketView from './views/TicketView.vue'
import EmployeesView from './views/EmployeesView.vue'
import ReportsView from './views/ReportsView.vue'

export default createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', component: DashboardView },
    { path: '/ny', component: NewTicketView },
    { path: '/sak/:id', component: TicketView },
    { path: '/ansatte', component: EmployeesView },
    { path: '/rapporter', component: ReportsView }
  ]
})

