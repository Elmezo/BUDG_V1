-- phpMyAdmin SQL Dump
-- version 5.2.1
-- https://www.phpmyadmin.net/
--
-- Host: 127.0.0.1
-- Generation Time: Nov 23, 2025 at 02:56 PM
-- Server version: 10.4.32-MariaDB
-- PHP Version: 8.2.12

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- Database: `project`
--

-- --------------------------------------------------------

--
-- Table structure for table `product_audit_history`
--

CREATE TABLE `product_audit_history` (
  `id` int(11) NOT NULL,
  `auditidpk` int(11) NOT NULL,
  `object` varchar(255) DEFAULT NULL,
  `event` varchar(255) DEFAULT NULL,
  `updateType` varchar(255) DEFAULT NULL,
  `field` varchar(255) DEFAULT NULL,
  `from` varchar(255) DEFAULT NULL,
  `to` varchar(255) DEFAULT NULL,
  `author` varchar(255) DEFAULT NULL,
  `date` datetime DEFAULT current_timestamp(),
  `lastChange` datetime DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `product_audit_history`
--

INSERT INTO `product_audit_history` (`id`, `auditidpk`, `object`, `event`, `updateType`, `field`, `from`, `to`, `author`, `date`, `lastChange`) VALUES
(1, 72, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'esraa Johnson', 'admin admin', '2025-10-30 16:42:27', '2025-10-30 16:42:27'),
(1, 73, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Product Steward', 'admin admin', '2025-10-30 16:42:27', '2025-10-30 16:42:27'),
(1, 74, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Not Active', 'admin admin', '2025-10-30 16:42:27', '2025-10-30 16:42:27'),
(19, 18, 'Product', 'Details', 'Added', 'Primary Name', NULL, 'gdfgdf', 'esraa Johnson', '2025-10-23 11:37:41', '2025-10-23 11:37:41'),
(19, 19, 'Product', 'Details', 'Added', 'Description', NULL, 'dfg', 'esraa Johnson', '2025-10-23 11:37:41', '2025-10-23 11:37:41'),
(19, 20, 'Product', 'Details', 'Added', 'Reference Number', NULL, 'fdg', 'esraa Johnson', '2025-10-23 11:37:41', '2025-10-23 11:37:41'),
(19, 21, 'Product', 'Details', 'Added', 'Parent Product', NULL, 'po66', 'esraa Johnson', '2025-10-23 11:37:41', '2025-10-23 11:37:41'),
(19, 22, 'Product', 'Details', 'Status Change', 'Status', NULL, 'Active', 'esraa Johnson', '2025-10-23 11:37:41', '2025-10-23 11:37:41'),
(19, 23, 'Product', 'Details', 'Status Change', 'Lifecycle Status', NULL, 'statuss', 'esraa Johnson', '2025-10-23 11:37:41', '2025-10-23 11:37:41'),
(19, 24, 'Product', 'Details', 'Added', 'Is Public', NULL, 'Public', 'esraa Johnson', '2025-10-23 11:37:42', '2025-10-23 11:37:42'),
(19, 25, 'Product', 'Details', 'Added', 'Last Update User', NULL, 'Alice Smith', 'esraa Johnson', '2025-10-23 11:37:42', '2025-10-23 11:37:42'),
(19, 26, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Admin', 'esraa Johnson', '2025-10-23 11:37:42', '2025-10-23 11:37:42'),
(19, 27, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'esraa Johnson', '2025-10-23 11:37:42', '2025-10-23 11:37:42'),
(19, 28, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'esraa Johnson', '2025-10-23 11:37:42', '2025-10-23 11:37:42'),
(20, 29, 'Product', 'Details', 'Added', 'Primary Name', NULL, 'hfgh', 'Alice Smith', '2025-10-23 16:45:06', '2025-10-23 16:45:06'),
(20, 30, 'Product', 'Details', 'Added', 'Description', NULL, 'fhghfg', 'Alice Smith', '2025-10-23 16:45:06', '2025-10-23 16:45:06'),
(20, 31, 'Product', 'Details', 'Added', 'Reference Number', NULL, 'fghf', 'Alice Smith', '2025-10-23 16:45:06', '2025-10-23 16:45:06'),
(20, 32, 'Product', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-10-23 16:45:06', '2025-10-23 16:45:06'),
(20, 33, 'Product', 'Details', 'Status Change', 'Lifecycle Status', NULL, 'statuss', 'Alice Smith', '2025-10-23 16:45:06', '2025-10-23 16:45:06'),
(20, 34, 'Product', 'Details', 'Added', 'Is Public', NULL, 'Public', 'Alice Smith', '2025-10-23 16:45:06', '2025-10-23 16:45:06'),
(20, 35, 'Product', 'Details', 'Added', 'Last Update User', NULL, 'Alice Smith', 'Alice Smith', '2025-10-23 16:45:06', '2025-10-23 16:45:06'),
(20, 36, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Admin', 'Alice Smith', '2025-10-23 16:45:06', '2025-10-23 16:45:06'),
(20, 37, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-10-23 16:45:06', '2025-10-23 16:45:06'),
(20, 38, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-23 16:45:06', '2025-10-23 16:45:06'),
(21, 39, 'Product', 'Details', 'Added', 'Primary Name', NULL, 'شdvfsdfg', 'Alice Smith', '2025-10-26 17:01:23', '2025-10-26 17:01:23'),
(21, 40, 'Product', 'Details', 'Added', 'Description', NULL, 'sdfgsdgdf', 'Alice Smith', '2025-10-26 17:01:23', '2025-10-26 17:01:23'),
(21, 41, 'Product', 'Details', 'Added', 'Reference Number', NULL, 'sdfg', 'Alice Smith', '2025-10-26 17:01:23', '2025-10-26 17:01:23'),
(21, 42, 'Product', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-10-26 17:01:23', '2025-10-26 17:01:23'),
(21, 43, 'Product', 'Details', 'Status Change', 'Lifecycle Status', NULL, 'statuss', 'Alice Smith', '2025-10-26 17:01:23', '2025-10-26 17:01:23'),
(21, 44, 'Product', 'Details', 'Added', 'Is Public', NULL, 'Public', 'Alice Smith', '2025-10-26 17:01:23', '2025-10-26 17:01:23'),
(21, 45, 'Product', 'Details', 'Added', 'Last Update User', NULL, 'Alice Smith', 'Alice Smith', '2025-10-26 17:01:23', '2025-10-26 17:01:23'),
(21, 46, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Admin', 'Alice Smith', '2025-10-26 17:01:24', '2025-10-26 17:01:24'),
(21, 47, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-10-26 17:01:24', '2025-10-26 17:01:24'),
(21, 48, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-26 17:01:24', '2025-10-26 17:01:24'),
(22, 49, 'Product', 'Details', 'Added', 'Primary Name', NULL, 'dfhghdfh', 'admin admin', '2025-10-28 11:54:17', '2025-10-28 11:54:17'),
(22, 50, 'Product', 'Details', 'Added', 'Description', NULL, 'fghfg', 'admin admin', '2025-10-28 11:54:17', '2025-10-28 11:54:17'),
(22, 51, 'Product', 'Details', 'Added', 'Reference Number', NULL, 'fhdghdfg', 'admin admin', '2025-10-28 11:54:17', '2025-10-28 11:54:17'),
(22, 52, 'Product', 'Details', 'Status Change', 'Status', NULL, 'Active', 'admin admin', '2025-10-28 11:54:17', '2025-10-28 11:54:17'),
(22, 53, 'Product', 'Details', 'Status Change', 'Lifecycle Status', NULL, 'statuss', 'admin admin', '2025-10-28 11:54:17', '2025-10-28 11:54:17'),
(22, 54, 'Product', 'Details', 'Added', 'Is Public', NULL, 'Public', 'admin admin', '2025-10-28 11:54:17', '2025-10-28 11:54:17'),
(22, 55, 'Product', 'Details', 'Added', 'Last Update User', NULL, 'Alice Smith', 'admin admin', '2025-10-28 11:54:17', '2025-10-28 11:54:17'),
(22, 56, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Admin', 'admin admin', '2025-10-28 11:54:17', '2025-10-28 11:54:17'),
(22, 57, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'admin admin', '2025-10-28 11:54:17', '2025-10-28 11:54:17'),
(22, 58, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'admin admin', '2025-10-28 11:54:17', '2025-10-28 11:54:17'),
(22, 59, 'Product', 'Details', 'Updated', 'Primary Name', 'dfhghdfh', 'Abdo', 'admin admin', '2025-10-28 11:54:43', '2025-10-28 11:54:43'),
(22, 60, 'Product', 'Details', 'Updated', 'Reference Number', 'fhdghdfg', 'Ref Abdo', 'admin admin', '2025-10-28 11:54:43', '2025-10-28 11:54:43'),
(23, 61, 'Product', 'Details', 'Added', 'Primary Name', NULL, 'maro', 'admin admin', '2025-10-28 12:11:07', '2025-10-28 12:11:07'),
(23, 62, 'Product', 'Details', 'Added', 'Description', NULL, 'maro desc', 'admin admin', '2025-10-28 12:11:07', '2025-10-28 12:11:07'),
(23, 63, 'Product', 'Details', 'Added', 'Reference Number', NULL, 'maro ref', 'admin admin', '2025-10-28 12:11:07', '2025-10-28 12:11:07'),
(23, 64, 'Product', 'Details', 'Status Change', 'Status', NULL, 'Active', 'admin admin', '2025-10-28 12:11:07', '2025-10-28 12:11:07'),
(23, 65, 'Product', 'Details', 'Status Change', 'Lifecycle Status', NULL, 'statuss', 'admin admin', '2025-10-28 12:11:07', '2025-10-28 12:11:07'),
(23, 66, 'Product', 'Details', 'Added', 'Is Public', NULL, 'Public', 'admin admin', '2025-10-28 12:11:07', '2025-10-28 12:11:07'),
(23, 67, 'Product', 'Details', 'Added', 'Last Update User', NULL, 'admin admin', 'admin admin', '2025-10-28 12:11:07', '2025-10-28 12:11:07'),
(23, 68, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Admin', 'admin admin', '2025-10-28 12:11:08', '2025-10-28 12:11:08'),
(23, 69, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'admin admin', '2025-10-28 12:11:08', '2025-10-28 12:11:08'),
(23, 70, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'admin admin', 'admin admin', '2025-10-28 12:11:08', '2025-10-28 12:11:08'),
(23, 71, 'Product', 'Details', 'Updated', 'Long Name', '', 'maro Long Name', 'Alice Smith', '2025-10-28 12:13:47', '2025-10-28 12:13:47'),
(23, 75, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'esraa Johnson', 'Alice Smith', '2025-11-09 10:54:29', '2025-11-09 10:54:29'),
(23, 76, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Product Steward', 'Alice Smith', '2025-11-09 10:54:29', '2025-11-09 10:54:29'),
(23, 77, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Not Active', 'Alice Smith', '2025-11-09 10:54:29', '2025-11-09 10:54:29'),
(24, 78, 'Product', 'Details', 'Added', 'Primary Name', NULL, 'product testx4', 'Alice Smith', '2025-11-09 14:38:37', '2025-11-09 14:38:37'),
(24, 79, 'Product', 'Details', 'Added', 'Description', NULL, '9/11', 'Alice Smith', '2025-11-09 14:38:37', '2025-11-09 14:38:37'),
(24, 80, 'Product', 'Details', 'Added', 'Long Name', NULL, 'longgg namee', 'Alice Smith', '2025-11-09 14:38:37', '2025-11-09 14:38:37'),
(24, 81, 'Product', 'Details', 'Added', 'Parent Product', NULL, 'product4', 'Alice Smith', '2025-11-09 14:38:37', '2025-11-09 14:38:37'),
(24, 82, 'Product', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-11-09 14:38:37', '2025-11-09 14:38:37'),
(24, 83, 'Product', 'Details', 'Status Change', 'Lifecycle Status', NULL, 'statuss', 'Alice Smith', '2025-11-09 14:38:37', '2025-11-09 14:38:37'),
(24, 84, 'Product', 'Details', 'Added', 'Is Public', NULL, 'Public', 'Alice Smith', '2025-11-09 14:38:37', '2025-11-09 14:38:37'),
(24, 85, 'Product', 'Details', 'Added', 'Last Update User', NULL, 'Alice Smith', 'Alice Smith', '2025-11-09 14:38:37', '2025-11-09 14:38:37'),
(24, 86, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Change Request OwnerR', 'Alice Smith', '2025-11-09 14:38:37', '2025-11-09 14:38:37'),
(24, 87, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-11-09 14:38:37', '2025-11-09 14:38:37'),
(24, 88, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-11-09 14:38:37', '2025-11-09 14:38:37'),
(24, 89, 'Product', 'Details', 'Updated', 'Description', '9/11', '9///11', 'Alice Smith', '2025-11-09 14:39:19', '2025-11-09 14:39:19'),
(24, 90, 'Stakeholder', 'delete', 'Deleted', 'Name', 'Alice Smith', NULL, 'System', '2025-11-09 14:39:21', '2025-11-09 14:39:21'),
(24, 91, 'Stakeholder', 'delete', 'Deleted', 'Role', 'Product Owner', NULL, 'System', '2025-11-09 14:39:21', '2025-11-09 14:39:21'),
(24, 92, 'Stakeholder', 'delete', 'Deleted', 'Role Status', 'Active', NULL, 'System', '2025-11-09 14:39:21', '2025-11-09 14:39:21'),
(24, 93, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Charlie Brown', 'Alice Smith', '2025-11-09 14:39:21', '2025-11-09 14:39:21'),
(24, 94, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Product Owner', 'Alice Smith', '2025-11-09 14:39:21', '2025-11-09 14:39:21'),
(24, 95, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-11-09 14:39:21', '2025-11-09 14:39:21');

--
-- Indexes for dumped tables
--

--
-- Indexes for table `product_audit_history`
--
ALTER TABLE `product_audit_history`
  ADD PRIMARY KEY (`id`,`auditidpk`),
  ADD UNIQUE KEY `auditidpk` (`auditidpk`);

--
-- AUTO_INCREMENT for dumped tables
--

--
-- AUTO_INCREMENT for table `product_audit_history`
--
ALTER TABLE `product_audit_history`
  MODIFY `auditidpk` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=96;

--
-- Constraints for dumped tables
--

--
-- Constraints for table `product_audit_history`
--
ALTER TABLE `product_audit_history`
  ADD CONSTRAINT `Product_Audit_history_ibfk_1` FOREIGN KEY (`id`) REFERENCES `product` (`id`);
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
