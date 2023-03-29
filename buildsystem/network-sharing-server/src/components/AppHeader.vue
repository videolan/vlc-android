<template>
  <nav class="navbar navbar-light navbar-expand-md ">
    <div class="navbar-nav d-flex align-items-center">
      <RouterLink class="navbar-brand " :to="{ name: 'Home' }">
        <span><img id="logo" src="../assets/resources/icon.png"></span>
        VLC-Android's WebServer
      </RouterLink>
      <RouterLink class="nav-item nav-link" :to="{ name: 'MediaUpload' }">
        Uploads
      </RouterLink>
    </div>
  </nav>
</template>

<script>
export default {

}
</script>

<style>
.navbar.navbar-light {
  background-color: #FF7700;
  border-color: #e7e7e7;
  box-shadow: 0 0 4px rgba(0, 0, 0, .14), 0 4px 8px rgba(0, 0, 0, .28);
  border-radius: 0;
  padding: 0;
}

.navbar {
  align-items: start;
  justify-content: flex-start;
}

.navbar-brand,
.navbar-brand:hover,
.navbar-brand:focus {
  color: white;
}

.nav-link,
.nav-link:hover,
.nav-link:focus {
  color: white;
}
</style>