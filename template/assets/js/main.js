/*-----------------------------------------------------------------

Template Name: WowTube - OTT Platform and Video Streaming HTML Template 
Author:  Namespace-IT
Author URI: https://themeforest.net/user/Namespace-IT/portfolio
Version: 1.0.0
Description: WowTube - OTT Platform and Video Streaming HTML Template <

-------------------------------------------------------------------
CSS TABLE OF CONTENTS
-------------------------------------------------------------------

01. header
02. magnificPopup
03. counter up
04. wow animation
05. nice select
06. search popup
07. swiper slider
08. mousecursor 
09. preloader 


------------------------------------------------------------------*/

(function($) {
    "use strict";

    $(document).ready( function() {

        //>> Mobile Menu Js Start <<//
        $('#mobile-menu').meanmenu({
            meanMenuContainer: '.mobile-menu',
            meanScreenWidth: "1199",
            meanExpand: ['<i class="far fa-plus"></i>'],
        });

        //>> Sidebar Toggle Js Start <<//
        $(".offcanvas__close,.offcanvas__overlay").on("click", function() {
            $(".offcanvas__info").removeClass("info-open");
            $(".offcanvas__overlay").removeClass("overlay-open");
        });
        $(".sidebar__toggle").on("click", function() {
            $(".offcanvas__info").addClass("info-open");
            $(".offcanvas__overlay").addClass("overlay-open");
        });

        //>> Body Overlay Js Start <<//
        $(".body-overlay").on("click", function() {
            $(".offcanvas__area").removeClass("offcanvas-opened");
            $(".df-search-area").removeClass("opened");;
            $(".body-overlay").removeClass("opened");
        });

        //>> Sticky Header Js Start <<//

        // $(window).scroll(function() {
        //     if ($(this).scrollTop() > 250) {
        //         $("#header-sticky").addClass("sticky");
        //     } else {
        //         $("#header-sticky").removeClass("sticky");
        //     }
        // });

        //>> Video Popup Start <<//
        $(".img-popup").magnificPopup({
            type: "image",
            gallery: {
                enabled: true,
            },
        });

        $('.video-popup').magnificPopup({
            type: 'iframe',
            callbacks: {
            }
        });
        
        //>> Counterup Start <<//
        $(".count").counterUp({
            delay: 15,
            time: 4000,
        });

        //>> Wow Animation Start <<//
        new WOW().init();

        //>> Nice Select Start <<//
        // $('select').niceSelect();
        $(document).ready(function () {
            // Initialize Nice Select Plugin
            $('select').niceSelect();  
        
            // Add hover functionality to open the dropdown
            $('.nice-select').hover(
                function () {
                    $(this).addClass('open');  // Show options when hovering
                }, 
                function () {
                    $(this).removeClass('open');  // Hide options when not hovering
                }
            );
        
            // Optional: Prevent dropdown from opening/closing on click
            $('.nice-select').on('click', function (e) {
                e.preventDefault();  // Stop click event from toggling the dropdown
            });
        });
        

        // Search bar
        $(".tp-search-toggle").on('click', function(){
            $(".tp-header-search-bar").addClass("tp-search-open");
            $(".tp-offcanvas-overlay").addClass("tp-offcanvas-overlay-open");
        });
  
        $(".tp-search-close,.tp-offcanvas-overlay").on('click', function(){
            $(".tp-header-search-bar").removeClass("tp-search-open");
            $(".tp-offcanvas-overlay").removeClass("tp-offcanvas-overlay-open");
        });

        //>> Hero Slider Start <<//
        const sliderswiper = new Swiper('.hero-slider', {
            //Optional parameters
            speed: 1500,
            loop: true,
            slidesPerView: 1,
            autoplay: true,
            effect: 'fade',
            breakpoints: {
                '1600': {
                    slidesPerView: 1,
                },
                '1400': {
                    slidesPerView: 1,
                },
                '1200': {
                    slidesPerView: 1,
                },
                '992': {
                    slidesPerView: 1,
                },
                '768': {
                    slidesPerView: 1,
                },
                '576': {
                    slidesPerView: 1,
                },
                '0': {
                    slidesPerView: 1,
                },

                a11y: false,
            },
            pagination: {
                el: ".dots",
                clickable: true,
            },

            navigation: {
                prevEl: ".array-next",
                nextEl: ".array-prev",
            },

        });
        
        //>> Banner Thumnail Slider Start <<//
        if($('.banner-thumnail-slider').length > 0) {
            const bannerThumnailSlider = new Swiper(".banner-thumnail-slider", {
            spaceBetween: 10,
            speed: 1500,
            loop: true,
            autoplay: true,
            // autoplay: {
            //     delay: 1000,
            //     disableOnInteraction: false,
            // },
            pagination: {
                el: ".dot",
                clickable: true,
            },
            breakpoints: {
                991: {
                    slidesPerView: 3,
                },
                767: {
                    slidesPerView: 2,
                },
                575: {
                    slidesPerView: 1,
                },
                0: {
                    slidesPerView: 1,
                },
            },

            navigation: {
                prevEl: ".array-next",
                nextEl: ".array-prev",
            },
                
            });
        }

        //>> Movie Slider Start <<//
        if($('.movie-slider').length > 0) {
            const movieSlider = new Swiper(".movie-slider", {
                spaceBetween: 30,
                speed: 2000,
                loop: true,
                pagination: {
                    el: ".dot2",
                    clickable: true,
                },
                navigation: {
                    nextEl: ".array-prev",
                    prevEl: ".array-next",
                },
                breakpoints: {
                    1499: {
                        slidesPerView: 5,
                    },
                    1199: {
                        slidesPerView: 4,
                    },
                    991: {
                        slidesPerView: 3,
                    },
                    767: {
                        slidesPerView: 2,
                    },
                    575: {
                        slidesPerView: 1,
                    },
                    0: {
                        slidesPerView: 1,
                    },
                },
            });
        }

        if($('.movie-slider-2').length > 0) {
            const movieSlider2 = new Swiper(".movie-slider-2", {
                spaceBetween: 30,
                speed: 2000,
                loop: true,
                centeredSlides: true,
                autoplay: {
                    delay: 1000,
                    disableOnInteraction: false,
                },
                pagination: {
                    el: ".dot",
                    clickable: true,
                },
                breakpoints: {
                    1399: {
                        slidesPerView: 4,
                    },
                    1199: {
                        slidesPerView: 3,
                    },
                    991: {
                        slidesPerView: 2,
                    },
                    767: {
                        slidesPerView: 2,
                    },
                    575: {
                        slidesPerView: 1,
                    },
                    0: {
                        slidesPerView: 1,
                    },
                },
            });
        }

        if($('.movie-slider-3').length > 0) {
            const movieSlider3 = new Swiper(".movie-slider-3", {
                spaceBetween: 30,
                speed: 2000,
                loop: true,
                navigation: {
                    nextEl: ".array-prev",
                    prevEl: ".array-next",
                },
                breakpoints: {
                    1699: {
                        slidesPerView: 6,
                    },
                    1199: {
                        slidesPerView: 4,
                    },
                    991: {
                        slidesPerView: 3,
                    },
                    767: {
                        slidesPerView: 2,
                    },
                    575: {
                        slidesPerView: 2,
                    },
                    0: {
                        slidesPerView: 1,
                    },
                },
            });
        }

        if($('.movie-category-slider').length > 0) {
            const movieCategorySlider = new Swiper(".movie-category-slider", {
                spaceBetween: 30,
                speed: 2000,
                loop: true,
                navigation: {
                    nextEl: ".array-prev",
                    prevEl: ".array-next",
                },
                breakpoints: {
                    1399: {
                        slidesPerView: 5,
                    },
                    1199: {
                        slidesPerView: 4,
                    },
                    991: {
                        slidesPerView: 3,
                    },
                    767: {
                        slidesPerView: 2,
                    },
                    575: {
                        slidesPerView: 1,
                    },
                    0: {
                        slidesPerView: 1,
                    },
                },
            });
        }

        //>> Tv Shows Slider Start <<//
        if($('.tv-shows-slider').length > 0) {
            const tvShowsSlider = new Swiper(".tv-shows-slider", {
                spaceBetween: 30,
                speed: 2000,
                loop: true,
                breakpoints: {
                  
                    1199: {
                        slidesPerView: 4,
                    },
                    991: {
                        slidesPerView: 3,
                    },
                    767: {
                        slidesPerView: 2,
                    },
                    575: {
                        slidesPerView: 1,
                    },
                    0: {
                        slidesPerView: 1,
                    },
                },
            });
        }

        //>> Exclusive Video Slider Start <<//
        if($('.exclusive-slider').length > 0) {
            const exclusiveSlider = new Swiper(".exclusive-slider", {
                spaceBetween: 30,
                speed: 2000,
                loop: true,
                navigation: {
                    nextEl: ".array-prev",
                    prevEl: ".array-next",
                },
                breakpoints: {
                    1199: {
                        slidesPerView: 3,
                    },
                    991: {
                        slidesPerView: 2,
                    },
                    767: {
                        slidesPerView: 2,
                    },
                    575: {
                        slidesPerView: 1,
                    },
                    0: {
                        slidesPerView: 1,
                    },
                },
            });
        }

        //>> Recently Video Slider Start <<//
        if($('.recently-videos-slider').length > 0) {
            const recentlyVideosSlider = new Swiper(".recently-videos-slider", {
                spaceBetween: 30,
                speed: 2000,
                loop: true,
                navigation: {
                    nextEl: ".array-prev",
                    prevEl: ".array-next",
                },
                breakpoints: {
                    1199: {
                        slidesPerView: 4,
                    },
                    991: {
                        slidesPerView: 3,
                    },
                    767: {
                        slidesPerView: 2,
                    },
                    575: {
                        slidesPerView: 1,
                    },
                    0: {
                        slidesPerView: 1,
                    },
                },
            });
        }

        //>> Trending Video Slider Start <<//
        if($('.trending-slider').length > 0) {
            const trendingSlider = new Swiper(".trending-slider", {
                spaceBetween: 30,
                speed: 2000,
                loop: true,
                navigation: {
                    nextEl: ".cta-prev",
                    prevEl: ".cta-next",
                },
                breakpoints: {
                    1199: {
                        slidesPerView: 4,
                    },
                    991: {
                        slidesPerView: 3,
                    },
                    767: {
                        slidesPerView: 2,
                    },
                    575: {
                        slidesPerView: 1,
                    },
                    0: {
                        slidesPerView: 1,
                    },
                },
            });
        }

        if($('.trending-slider-2').length > 0) {
            const trendingSlider2 = new Swiper(".trending-slider-2", {
                spaceBetween: 30,
                speed: 2000,
                loop: true,
                navigation: {
                    nextEl: ".array-prev",
                    prevEl: ".array-next",
                },
                breakpoints: {
                    1199: {
                        slidesPerView: 4,
                    },
                    991: {
                        slidesPerView: 3,
                    },
                    767: {
                        slidesPerView: 2,
                    },
                    575: {
                        slidesPerView: 1,
                    },
                    0: {
                        slidesPerView: 1,
                    },
                },
            });
        }

        if($('.trending-movie-slider').length > 0) {
            const trendingMovieSlider = new Swiper(".trending-movie-slider", {
                spaceBetween: 30,
                speed: 2000,
                loop: true,
                navigation: {
                    nextEl: ".cta-prev",
                    prevEl: ".cta-next",
                },
                pagination: {
                    el: ".movie-dot",
                },
                breakpoints: {
                    1399: {
                        slidesPerView: 3,
                    },
                    1199: {
                        slidesPerView: 2,
                    },
                    991: {
                        slidesPerView: 3,
                    },
                    767: {
                        slidesPerView: 2,
                    },
                    575: {
                        slidesPerView: 1,
                    },
                    0: {
                        slidesPerView: 1,
                    },
                },
            });
        }

        //>> Cta Thumnail Slider Start <<//
        if($('.cta-thumnail-slider').length > 0) {
            const ctaThumnailSlider = new Swiper(".cta-thumnail-slider", {
                spaceBetween: 30,
                speed: 2000,
                loop: true,
                navigation: {
                    nextEl: ".cta-prev",
                    prevEl: ".cta-next",
                },
                breakpoints: {
                    991: {
                        slidesPerView: 2,
                    },
                    767: {
                        slidesPerView: 2,
                    },
                    575: {
                        slidesPerView: 2,
                    },
                    0: {
                        slidesPerView: 1,
                    },
                },
            });
        }

        //>> Upcoming Movie Slider Start <<//
        if($('.upcoming-movie-slider').length > 0) {
            const upcomingMovieSlider = new Swiper(".upcoming-movie-slider", {
                spaceBetween: 30,
                speed: 2000,
                loop: true,
                navigation: {
                    nextEl: ".array-prev",
                    prevEl: ".array-next",
                },
                breakpoints: {
                    1699: {
                        slidesPerView: 6,
                    },
                    1499: {
                        slidesPerView: 5,
                    },
                    1199: {
                        slidesPerView: 4,
                    },
                    991: {
                        slidesPerView: 3,
                    },
                    767: {
                        slidesPerView: 3,
                    },
                    575: {
                        slidesPerView: 2,
                    },
                    0: {
                        slidesPerView: 1,
                    },
                },
            });
        }

        //>> Web Series Slider Start <<//
        if($('.web-series-slider').length > 0) {
            const webSeriesSlider = new Swiper(".web-series-slider", {
                spaceBetween: 30,
                speed: 2000,
                loop: true,
                navigation: {
                    nextEl: ".cta-prev",
                    prevEl: ".cta-next",
                },
                breakpoints: {
                    1399: {
                        slidesPerView: 4,
                    },
                    991: {
                        slidesPerView: 3,
                    },
                    767: {
                        slidesPerView: 2,
                    },
                    575: {
                        slidesPerView: 1,
                    },
                    0: {
                        slidesPerView: 1,
                    },
                },
            });
        }

        //>> Cta Image Slider Start <<//
        if($('.cta-movie-slider').length > 0) {
            const ctaMovieSlider = new Swiper(".cta-movie-slider", {
                spaceBetween: 30,
                speed: 2000,
                direction: "vertical",
                loop: true,
                autoplay: {
                    delay: 1000,
                    disableOnInteraction: false,
                },
                pagination: {
                    el: ".dot",
                    clickable: true,
                },
                breakpoints: {
                    767: {
                        slidesPerView: 3,
                    },
                    575: {
                        slidesPerView: 2,
                    },
                    0: {
                        slidesPerView: 1,
                    },
                },
            });
        }

        //>> Cast Slider Start <<//
        if($('.cast-slider').length > 0) {
            const castSlider = new Swiper(".cast-slider", {
                spaceBetween: 30,
                speed: 2000,
                loop: true,
                autoplay: {
                    delay: 1000,
                    disableOnInteraction: false,
                },
                navigation: {
                    nextEl: ".cta-prev",
                    prevEl: ".cta-next",
                },
                breakpoints: {
                    1399: {
                        slidesPerView: 8,
                    },
                    1199: {
                        slidesPerView: 6,
                    },
                    991: {
                        slidesPerView: 6,
                    },
                    767: {
                        slidesPerView: 4,
                    },
                    575: {
                        slidesPerView: 3,
                    },
                    0: {
                        slidesPerView: 1,
                    },
                },
            });
        }

        if($('.cast-slider-2').length > 0) {
            const castSlider2 = new Swiper(".cast-slider-2", {
                spaceBetween: 30,
                speed: 2000,
                loop: true,
                breakpoints: {
                    1399: {
                        slidesPerView: 10,
                    },
                    1199: {
                        slidesPerView: 8,
                    },
                    991: {
                        slidesPerView: 5,
                    },
                    767: {
                        slidesPerView: 4,
                    },
                    575: {
                        slidesPerView: 3,
                    },
                    0: {
                        slidesPerView: 1,
                    },
                },
            });
        }

        //>> Review Slider Start <<//
        if($('.review-slider').length > 0) {
            const reviewSlider = new Swiper(".review-slider", {
                spaceBetween: 20,
                speed: 2000,
                loop: true,
                autoplay: {
                    delay: 1000,
                    disableOnInteraction: false,
                },
                pagination: {
                    el: ".dot",
                    clickable: true,
                },
                navigation: {
                    nextEl: ".array-prev",
                    prevEl: ".array-next",
                },
                breakpoints: {
                    1199: {
                        slidesPerView: 2,
                    },
                    991: {
                        slidesPerView: 1,
                    },
                    767: {
                        slidesPerView: 1,
                    },
                    575: {
                        slidesPerView: 1,
                    },
                    0: {
                        slidesPerView: 1,
                    },
                },
            });
        }

        //>> Brand Slider Start <<//
        if($('.brand-slider').length > 0) {
            const brandSlider = new Swiper(".brand-slider", {
                spaceBetween: 30,
                speed: 2000,
                loop: true,
                autoplay: {
                    delay: 1000,
                    disableOnInteraction: false,
                },
                breakpoints: {
                    1199: {
                        slidesPerView: 5,
                    },
                    991: {
                        slidesPerView: 4,
                    },
                    767: {
                        slidesPerView: 3,
                    },
                    575: {
                        slidesPerView: 2,
                    },
                    0: {
                        slidesPerView: 1,
                    },
                },
            });
        }

        //>> Video Details Slider Start <<//
        if($('.video-details-slider').length > 0) {
            const videoDetailsSlider = new Swiper(".video-details-slider", {
                spaceBetween: 5,
                speed: 2000,
                loop: true,
                autoplay: {
                    delay: 1000,
                    disableOnInteraction: false,
                },
                pagination: {
                    el: ".dot",
                    clickable: true,
                },
                breakpoints: {
                    1199: {
                        slidesPerView: 4,
                    },
                    991: {
                        slidesPerView: 3,
                    },
                    767: {
                        slidesPerView: 1,
                    },
                    575: {
                        slidesPerView: 1,
                    },
                    0: {
                        slidesPerView: 1,
                    },
                },
            });
        }

        //>> Testimonial Slider Start <<//
        if($('.testimonial-slider').length > 0) {
            const testimonialSlider = new Swiper(".testimonial-slider", {
                spaceBetween: 30,
                speed: 2000,
                loop: true,
                autoplay: {
                    delay: 1000,
                    disableOnInteraction: false,
                },
                pagination: {
                    el: ".dot",
                    clickable: true,
                },
                navigation: {
                    nextEl: ".array-prev",
                    prevEl: ".array-next",
                },
                breakpoints: {
                    1399: {
                        slidesPerView: 4,
                    },
                    1199: {
                        slidesPerView: 3,
                    },
                    991: {
                        slidesPerView: 2,
                    },
                    767: {
                        slidesPerView: 2,
                    },
                    575: {
                        slidesPerView: 1,
                    },
                    0: {
                        slidesPerView: 1,
                    },
                },
            });
        }

        //>> Mouse Cursor Start <<//
        function mousecursor() {
            if ($("body")) {
                const e = document.querySelector(".cursor-inner"),
                    t = document.querySelector(".cursor-outer");
                let n,
                    i = 0,
                    o = !1;
                (window.onmousemove = function(s) {
                    o ||
                        (t.style.transform =
                            "translate(" + s.clientX + "px, " + s.clientY + "px)"),
                        (e.style.transform =
                            "translate(" + s.clientX + "px, " + s.clientY + "px)"),
                        (n = s.clientY),
                        (i = s.clientX);
                }),
                $("body").on("mouseenter", "a, .cursor-pointer", function() {
                        e.classList.add("cursor-hover"), t.classList.add("cursor-hover");
                    }),
                    $("body").on("mouseleave", "a, .cursor-pointer", function() {
                        ($(this).is("a") && $(this).closest(".cursor-pointer").length) ||
                        (e.classList.remove("cursor-hover"),
                            t.classList.remove("cursor-hover"));
                    }),
                    (e.style.visibility = "visible"),
                    (t.style.visibility = "visible");
            }
        }
        $(function() {
            mousecursor();
        });

        // 13.Back to top btn    
        $(window).scroll(function () {
            if ($(this).scrollTop() > 20) {
                $("#back-top").addClass("show");
            } else {
                $("#back-top").removeClass("show");
            }
        });
        $("#back-top").click(function () {
            $("html, body").animate({ scrollTop: 0 }, 800);
            return false;
        });

    }); // End Document Ready Function

    function loader() {
        $(window).on('load', function() {
            // Animate loader off screen
            $(".preloader").addClass('loaded');                    
            $(".preloader").delay(600).fadeOut();                       
        });
    }

    loader();
   

})(jQuery); // End jQuery

